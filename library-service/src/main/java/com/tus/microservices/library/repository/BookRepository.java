package com.tus.microservices.library.repository;

import com.tus.microservices.library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Book entity operations.
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Find book by ISBN.
     */
    Optional<Book> findByIsbn(String isbn);

    /**
     * Check if book exists by ISBN.
     */
    boolean existsByIsbn(String isbn);

    /**
     * Find books by library ID.
     */
    List<Book> findByLibraryId(Long libraryId);

    /**
     * Find books by author prefix (case-insensitive).
     */
    List<Book> findByAuthorStartingWithIgnoreCase(String author);

    /**
     * Find books by title prefix (case-insensitive).
     */
    List<Book> findByTitleStartingWithIgnoreCase(String title);

    /**
     * Find books by genre.
     */
    List<Book> findByGenreIgnoreCase(String genre);

    /**
     * Find books by publication year.
     */
    List<Book> findByPublicationYear(Integer year);

    /**
     * Search books by multiple optional prefix filters.
     */
    @Query("""
        SELECT b FROM Book b
        WHERE (
            (:textPrefix IS NULL AND :queryIsbnPrefix IS NULL)
            OR (
                :textPrefix IS NOT NULL
                AND (
                    LOWER(b.title) LIKE LOWER(CONCAT(:textPrefix, '%'))
                    OR LOWER(COALESCE(b.author, '')) LIKE LOWER(CONCAT(:textPrefix, '%'))
                    OR LOWER(COALESCE(b.genre, '')) LIKE LOWER(CONCAT(:textPrefix, '%'))
                )
            )
            OR (
                :queryIsbnPrefix IS NOT NULL
                AND b.isbn LIKE CONCAT(:queryIsbnPrefix, '%')
            )
        )
        AND (
            :isbnPrefix IS NULL
            OR b.isbn LIKE CONCAT(:isbnPrefix, '%')
        )
        AND (
            :genrePrefix IS NULL
            OR LOWER(COALESCE(b.genre, '')) LIKE LOWER(CONCAT(:genrePrefix, '%'))
        )
        AND (
            :year IS NULL
            OR b.publicationYear = :year
        )
        ORDER BY b.title ASC
        """)
    List<Book> searchBooks(
        @Param("textPrefix") String textPrefix,
        @Param("queryIsbnPrefix") String queryIsbnPrefix,
        @Param("isbnPrefix") String isbnPrefix,
        @Param("genrePrefix") String genrePrefix,
        @Param("year") Integer year
    );
}
