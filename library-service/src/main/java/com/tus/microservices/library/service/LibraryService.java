package com.tus.microservices.library.service;

import com.tus.microservices.library.dto.CreateLibraryDTO;
import com.tus.microservices.library.dto.LibraryDTO;
import com.tus.microservices.library.dto.UpdateLibraryDTO;
import com.tus.microservices.library.entity.Library;
import com.tus.microservices.library.exception.DuplicateResourceException;
import com.tus.microservices.library.exception.ResourceNotFoundException;
import com.tus.microservices.library.repository.LibraryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service class for Library operations.
 */
@Service
@Transactional
public class LibraryService {

    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);

    private final LibraryRepository libraryRepository;

    public LibraryService(LibraryRepository libraryRepository) {
        this.libraryRepository = libraryRepository;
    }

    /**
     * Get all libraries.
     */
    @Transactional(readOnly = true)
    public List<LibraryDTO> getAllLibraries() {
        log.debug("Fetching all libraries");
        return libraryRepository.findAll().stream()
            .map(this::toDTO)
            .toList();
    }

    /**
     * Get library by ID.
     */
    @Transactional(readOnly = true)
    public LibraryDTO getLibraryById(Long id) {
        log.debug("Fetching library with id: {}", id);
        Library library = libraryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Library", "id", id));
        return toDTO(library);
    }

    /**
     * Create a new library.
     */
    public LibraryDTO createLibrary(CreateLibraryDTO dto) {
        log.debug("Creating library: {}", dto.name());

        if (libraryRepository.existsByName(dto.name())) {
            throw new DuplicateResourceException("Library", "name", dto.name());
        }

        Library library = new Library(
            dto.name(),
            dto.address(),
            dto.city(),
            dto.country()
        );

        Library saved = libraryRepository.save(library);
        log.info("Created library with id: {}", saved.getId());
        return toDTO(saved);
    }

    /**
     * Update an existing library.
     */
    public LibraryDTO updateLibrary(Long id, UpdateLibraryDTO dto) {
        log.debug("Updating library with id: {}", id);

        Library library = libraryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Library", "id", id));

        if (dto.name() != null && !dto.name().isBlank()) {
            // Check for duplicate name (if changing)
            if (!dto.name().equals(library.getName()) && libraryRepository.existsByName(dto.name())) {
                throw new DuplicateResourceException("Library", "name", dto.name());
            }
            library.setName(dto.name());
        }
        if (dto.address() != null && !dto.address().isBlank()) {
            library.setAddress(dto.address());
        }
        if (dto.city() != null) {
            library.setCity(dto.city());
        }
        if (dto.country() != null) {
            library.setCountry(dto.country());
        }

        Library updated = libraryRepository.save(library);
        log.info("Updated library with id: {}", id);
        return toDTO(updated);
    }

    /**
     * Delete a library.
     */
    public void deleteLibrary(Long id) {
        log.debug("Deleting library with id: {}", id);

        if (!libraryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Library", "id", id);
        }

        libraryRepository.deleteById(id);
        log.info("Deleted library with id: {}", id);
    }

    /**
     * Search libraries by city.
     */
    @Transactional(readOnly = true)
    public List<LibraryDTO> getLibrariesByCity(String city) {
        log.debug("Fetching libraries in city: {}", city);
        return libraryRepository.findByCityIgnoreCase(city).stream()
            .map(this::toDTO)
            .toList();
    }

    /**
     * Convert entity to DTO.
     */
    private LibraryDTO toDTO(Library library) {
        return new LibraryDTO(
            library.getId(),
            library.getName(),
            library.getAddress(),
            library.getCity(),
            library.getCountry(),
            library.getBooks().size(),
            library.getCreatedAt(),
            library.getUpdatedAt()
        );
    }
}
