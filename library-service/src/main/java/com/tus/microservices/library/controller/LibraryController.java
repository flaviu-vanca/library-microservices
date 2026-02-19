package com.tus.microservices.library.controller;

import com.tus.microservices.library.dto.CreateLibraryDTO;
import com.tus.microservices.library.dto.LibraryDTO;
import com.tus.microservices.library.dto.UpdateLibraryDTO;
import com.tus.microservices.library.service.LibraryService;
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
 * REST controller for Library operations.
 */
@RestController
@RequestMapping("/api/libraries")
@Tag(name = "Libraries", description = "Library management endpoints")
public class LibraryController {

    private static final Logger log = LoggerFactory.getLogger(LibraryController.class);

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping
    @Operation(summary = "Get all libraries", description = "Returns a list of all libraries")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved libraries")
    public ResponseEntity<List<LibraryDTO>> getAllLibraries() {
        log.info("GET /api/libraries");
        List<LibraryDTO> libraries = libraryService.getAllLibraries();
        return ResponseEntity.ok(libraries);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get library by ID", description = "Returns a single library by its ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved library"),
        @ApiResponse(responseCode = "404", description = "Library not found")
    })
    public ResponseEntity<LibraryDTO> getLibraryById(
            @Parameter(description = "Library ID") @PathVariable Long id) {
        log.info("GET /api/libraries/{}", id);
        LibraryDTO library = libraryService.getLibraryById(id);
        return ResponseEntity.ok(library);
    }

    @PostMapping
    @Operation(summary = "Create a new library", description = "Creates a new library and returns it")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Library created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "409", description = "Library with this name already exists")
    })
    public ResponseEntity<LibraryDTO> createLibrary(
            @Valid @RequestBody CreateLibraryDTO dto) {
        log.info("POST /api/libraries - Creating library: {}", dto.name());
        LibraryDTO created = libraryService.createLibrary(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a library", description = "Updates an existing library")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Library updated successfully"),
        @ApiResponse(responseCode = "404", description = "Library not found"),
        @ApiResponse(responseCode = "409", description = "Library with this name already exists")
    })
    public ResponseEntity<LibraryDTO> updateLibrary(
            @Parameter(description = "Library ID") @PathVariable Long id,
            @Valid @RequestBody UpdateLibraryDTO dto) {
        log.info("PUT /api/libraries/{}", id);
        LibraryDTO updated = libraryService.updateLibrary(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a library", description = "Deletes a library by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Library deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Library not found")
    })
    public ResponseEntity<Void> deleteLibrary(
            @Parameter(description = "Library ID") @PathVariable Long id) {
        log.info("DELETE /api/libraries/{}", id);
        libraryService.deleteLibrary(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search libraries by city", description = "Returns libraries in a specific city")
    public ResponseEntity<List<LibraryDTO>> getLibrariesByCity(
            @Parameter(description = "City name") @RequestParam String city) {
        log.info("GET /api/libraries/search?city={}", city);
        List<LibraryDTO> libraries = libraryService.getLibrariesByCity(city);
        return ResponseEntity.ok(libraries);
    }
}
