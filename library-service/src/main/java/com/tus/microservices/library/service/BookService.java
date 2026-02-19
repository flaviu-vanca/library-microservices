package com.tus.microservices.library.service;

import com.tus.microservices.library.dto.*;
import com.tus.microservices.library.entity.Book;
import com.tus.microservices.library.entity.Library;
import com.tus.microservices.library.exception.DuplicateResourceException;
import com.tus.microservices.library.exception.ResourceNotFoundException;
import com.tus.microservices.library.repository.BookRepository;
import com.tus.microservices.library.repository.LibraryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Service class for Book operations.
 */
@Service
@Transactional
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final Pattern ISBN_PREFIX_PATTERN = Pattern.compile("[0-9-]+");

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final InventoryClient inventoryClient;

    public BookService(BookRepository bookRepository,
                       LibraryRepository libraryRepository,
                       InventoryClient inventoryClient) {
        this.bookRepository = bookRepository;
        this.libraryRepository = libraryRepository;
        this.inventoryClient = inventoryClient;
    }

    /**
     * Get all books.
     */
    @Transactional(readOnly = true)
    public List<BookDTO> getAllBooks() {
        log.debug("Fetching all books");
        return bookRepository.findAll().stream()
            .map(this::toDTO)
            .toList();
    }

    /**
     * Get book by ID.
     */
    @Transactional(readOnly = true)
    public BookDTO getBookById(Long id) {
        log.debug("Fetching book with id: {}", id);
        return toDTO(findBookById(id));
    }

    /**
     * Get book by ISBN.
     */
    @Transactional(readOnly = true)
    public BookDTO getBookByIsbn(String isbn) {
        log.debug("Fetching book with ISBN: {}", isbn);
        Book book = bookRepository.findByIsbn(isbn)
            .orElseThrow(() -> new ResourceNotFoundException("Book", "ISBN", isbn));
        return toDTO(book);
    }

    /**
     * Get book availability (combines book info with inventory status).
     * This method calls the Inventory Service.
     */
    @Transactional(readOnly = true)
    public BookAvailabilityDTO getBookAvailability(Long id) {
        log.debug("Fetching availability for book id: {}", id);

        Book book = findBookById(id);
        Library library = book.getLibrary();

        // Call Inventory Service (with circuit breaker)
        InventoryStatusDTO inventory = inventoryClient.getInventoryStatus(book.getIsbn());

        return new BookAvailabilityDTO(
            book.getId(),
            book.getIsbn(),
            book.getTitle(),
            book.getAuthor(),
            book.getPublicationYear(),
            book.getGenre(),
            library != null ? library.getId() : null,
            library != null ? library.getName() : null,
            inventory
        );
    }

    /**
     * Create a new book.
     */
    public BookDTO createBook(CreateBookDTO dto) {
        log.debug("Creating book with ISBN: {}", dto.isbn());

        if (bookRepository.existsByIsbn(dto.isbn())) {
            throw new DuplicateResourceException("Book", "ISBN", dto.isbn());
        }

        Book book = new Book(
            dto.isbn(),
            dto.title(),
            dto.author(),
            dto.publicationYear(),
            dto.genre()
        );

        // Associate with library if provided
        if (dto.libraryId() != null) {
            book.setLibrary(findLibraryById(dto.libraryId()));
        }

        Book saved = bookRepository.save(book);
        log.info("Created book with id: {}", saved.getId());
        return toDTO(saved);
    }

    /**
     * Update an existing book.
     */
    public BookDTO updateBook(Long id, UpdateBookDTO dto) {
        log.debug("Updating book with id: {}", id);

        Book book = findBookById(id);

        if (dto.title() != null && !dto.title().isBlank()) {
            book.setTitle(dto.title());
        }
        if (dto.author() != null) {
            book.setAuthor(dto.author());
        }
        if (dto.publicationYear() != null) {
            book.setPublicationYear(dto.publicationYear());
        }
        if (dto.genre() != null) {
            book.setGenre(dto.genre());
        }
        if (dto.libraryId() != null) {
            book.setLibrary(findLibraryById(dto.libraryId()));
        }

        Book updated = bookRepository.save(book);
        log.info("Updated book with id: {}", id);
        return toDTO(updated);
    }

    /**
     * Delete a book.
     */
    public void deleteBook(Long id) {
        log.debug("Deleting book with id: {}", id);

        if (!bookRepository.existsById(id)) {
            throw new ResourceNotFoundException("Book", "id", id);
        }

        bookRepository.deleteById(id);
        log.info("Deleted book with id: {}", id);
    }

    /**
     * Search books by prefix for text fields and ISBN, with optional genre/year filters.
     */
    @Transactional(readOnly = true)
    public List<BookDTO> searchBooks(String query, String isbn, Integer year, String genre) {
        String normalizedQuery = normalizeFilter(query);
        String normalizedIsbn = normalizeFilter(isbn);
        String normalizedGenreInput = normalizeFilter(genre);
        String normalizedGenrePrefix = resolveTextPrefix(normalizedGenreInput);
        GeneralSearch generalSearch = resolveGeneralSearch(normalizedQuery);

        log.debug(
            "Searching books with filters query='{}', textPrefix='{}', queryIsbnPrefix='{}', isbnPrefix='{}', year='{}', genrePrefix='{}'",
            normalizedQuery,
            generalSearch.textPrefix(),
            generalSearch.isbnPrefix(),
            normalizedIsbn,
            year,
            normalizedGenrePrefix
        );

        boolean filtersRequested = normalizedQuery != null
            || normalizedIsbn != null
            || normalizedGenreInput != null
            || year != null;

        if (!filtersRequested) {
            return getAllBooks();
        }

        if (!generalSearch.hasSearchablePrefix()
            && normalizedIsbn == null
            && normalizedGenrePrefix == null
            && year == null) {
            return List.of();
        }

        return bookRepository.searchBooks(
                generalSearch.textPrefix(),
                generalSearch.isbnPrefix(),
                normalizedIsbn,
                normalizedGenrePrefix,
                year
            ).stream()
            .map(this::toDTO)
            .toList();
    }

    /**
     * Get books by library ID.
     */
    @Transactional(readOnly = true)
    public List<BookDTO> getBooksByLibrary(Long libraryId) {
        log.debug("Fetching books for library id: {}", libraryId);
        return bookRepository.findByLibraryId(libraryId).stream()
            .map(this::toDTO)
            .toList();
    }

    /**
     * Convert entity to DTO.
     */
    private BookDTO toDTO(Book book) {
        Library library = book.getLibrary();
        return new BookDTO(
            book.getId(),
            book.getIsbn(),
            book.getTitle(),
            book.getAuthor(),
            book.getPublicationYear(),
            book.getGenre(),
            library != null ? library.getId() : null,
            library != null ? library.getName() : null,
            book.getCreatedAt()
        );
    }

    private Book findBookById(Long id) {
        return bookRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Book", "id", id));
    }

    private Library findLibraryById(Long libraryId) {
        return libraryRepository.findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("Library", "id", libraryId));
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveTextPrefix(String value) {
        if (value == null || value.length() < MIN_SEARCH_LENGTH) {
            return null;
        }

        return value;
    }

    private GeneralSearch resolveGeneralSearch(String query) {
        if (query == null) {
            return new GeneralSearch(null, null);
        }

        if (isIsbnLike(query)) {
            return new GeneralSearch(null, query);
        }

        return new GeneralSearch(resolveTextPrefix(query), null);
    }

    private boolean isIsbnLike(String value) {
        return ISBN_PREFIX_PATTERN.matcher(value).matches();
    }

    private record GeneralSearch(String textPrefix, String isbnPrefix) {
        private boolean hasSearchablePrefix() {
            return textPrefix != null || isbnPrefix != null;
        }
    }
}
