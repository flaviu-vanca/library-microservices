package com.tus.microservices.library.controller;

import com.tus.microservices.library.dto.*;
import com.tus.microservices.library.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Book operations.
 * Includes the /availability endpoint that calls the Inventory Service.
 */
@RestController
@RequestMapping("/api/books")
@Tag(name = "Books", description = "Book management endpoints")
public class BookController {

    private static final Logger log = LoggerFactory.getLogger(BookController.class);

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    @Operation(summary = "Get all books", description = "Returns a list of all books")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved books")
    public ResponseEntity<List<BookDTO>> getAllBooks() {
        log.info("GET /api/books");
        List<BookDTO> books = bookService.getAllBooks();
        return ResponseEntity.ok(books);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get book by ID", description = "Returns a single book by its ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved book"),
        @ApiResponse(responseCode = "404", description = "Book not found")
    })
    public ResponseEntity<BookDTO> getBookById(
            @Parameter(description = "Book ID") @PathVariable Long id) {
        log.info("GET /api/books/{}", id);
        BookDTO book = bookService.getBookById(id);
        return ResponseEntity.ok(book);
    }

    @GetMapping("/{id}/availability")
    @Operation(
        summary = "Get book availability",
        description = "Returns book info with inventory status from Inventory Service. " +
                      "This endpoint demonstrates inter-service communication with circuit breaker."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved availability"),
        @ApiResponse(responseCode = "404", description = "Book not found")
    })
    public ResponseEntity<BookAvailabilityDTO> getBookAvailability(
            @Parameter(description = "Book ID") @PathVariable Long id) {
        log.info("GET /api/books/{}/availability - Calling Inventory Service", id);
        BookAvailabilityDTO availability = bookService.getBookAvailability(id);
        return ResponseEntity.ok(availability);
    }

    @GetMapping("/isbn/{isbn}")
    @Operation(summary = "Get book by ISBN", description = "Returns a book by its ISBN")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved book"),
        @ApiResponse(responseCode = "404", description = "Book not found")
    })
    public ResponseEntity<BookDTO> getBookByIsbn(
            @Parameter(description = "Book ISBN") @PathVariable String isbn) {
        log.info("GET /api/books/isbn/{}", isbn);
        BookDTO book = bookService.getBookByIsbn(isbn);
        return ResponseEntity.ok(book);
    }

    @PostMapping
    @Operation(summary = "Create a new book", description = "Creates a new book and returns it")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Book created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "409", description = "Book with this ISBN already exists")
    })
    public ResponseEntity<BookDTO> createBook(
            @Valid @RequestBody CreateBookDTO dto) {
        log.info("POST /api/books - Creating book with ISBN: {}", dto.isbn());
        BookDTO created = bookService.createBook(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a book", description = "Updates an existing book")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Book updated successfully"),
        @ApiResponse(responseCode = "404", description = "Book not found")
    })
    public ResponseEntity<BookDTO> updateBook(
            @Parameter(description = "Book ID") @PathVariable Long id,
            @Valid @RequestBody UpdateBookDTO dto) {
        log.info("PUT /api/books/{}", id);
        BookDTO updated = bookService.updateBook(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a book", description = "Deletes a book by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Book deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Book not found")
    })
    public ResponseEntity<Void> deleteBook(
            @Parameter(description = "Book ID") @PathVariable Long id) {
        log.info("DELETE /api/books/{}", id);
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(
        summary = "Search books",
        description = "Search books by title, author, genre, or ISBN prefix, with an optional exact publication year filter"
    )
    public ResponseEntity<List<BookDTO>> searchBooks(
            @Parameter(description = "Prefix search query for title, author, genre, or ISBN")
            @RequestParam(required = false) String q,
            @Parameter(description = "ISBN prefix filter")
            @RequestParam(required = false) String isbn,
            @Parameter(description = "Publication year filter")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "Genre prefix filter")
            @RequestParam(required = false) String genre) {
        log.info("GET /api/books/search?q={}&isbn={}&year={}&genre={}", q, isbn, year, genre);
        List<BookDTO> books = bookService.searchBooks(q, isbn, year, genre);
        return ResponseEntity.ok(books);
    }

    @GetMapping("/library/{libraryId}")
    @Operation(summary = "Get books by library", description = "Returns all books in a specific library")
    public ResponseEntity<List<BookDTO>> getBooksByLibrary(
            @Parameter(description = "Library ID") @PathVariable Long libraryId) {
        log.info("GET /api/books/library/{}", libraryId);
        List<BookDTO> books = bookService.getBooksByLibrary(libraryId);
        return ResponseEntity.ok(books);
    }
}
