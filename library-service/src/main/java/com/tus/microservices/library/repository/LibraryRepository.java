package com.tus.microservices.library.repository;

import com.tus.microservices.library.entity.Library;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Library entity operations.
 */
@Repository
public interface LibraryRepository extends JpaRepository<Library, Long> {

    /**
     * Find libraries by city (case-insensitive).
     */
    List<Library> findByCityIgnoreCase(String city);

    /**
     * Find libraries by country (case-insensitive).
     */
    List<Library> findByCountryIgnoreCase(String country);

    /**
     * Find library by name (exact match).
     */
    Optional<Library> findByName(String name);

    /**
     * Find libraries with name containing search term.
     */
    List<Library> findByNameContainingIgnoreCase(String name);

    /**
     * Check if library exists by name.
     */
    boolean existsByName(String name);
}
