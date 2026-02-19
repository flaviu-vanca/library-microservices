package com.tus.microservices.library.repository;

import com.tus.microservices.library.entity.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.cloud.config.client.ConfigClientAutoConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@ImportAutoConfiguration(exclude = ConfigClientAutoConfiguration.class)
class BookRepositoryTest {

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void setUp() {
        bookRepository.saveAllAndFlush(List.of(
            book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", "Technology"),
            book("978-1-4493-6130-9", "Designing Data-Intensive Applications", "Martin Kleppmann", "Technology"),
            book("978-0-321-12742-6", "Patterns of Enterprise Application Architecture", "Martin Fowler", "Architecture"),
            book("978-0-10000-123-4", "Microservices Patterns", "Chris Richardson", "Technology"),
            book("978-0-7653-4321-0", "Catalog of Clouds", "Carla Sutton", "Cataloging"),
            book("978-1-2345-6789-0", "Scala for the Impatient", "Jane Doe", "Education")
        ));
    }

    @Test
    void searchBooks_findsValidTwoCharacterTitlePrefix() {
        List<Book> results = bookRepository.searchBooks("Ca", null, null, null, null);

        assertThat(results)
            .extracting(Book::getTitle)
            .containsExactly("Catalog of Clouds");
    }

    @Test
    void searchBooks_findsValidTwoCharacterAuthorPrefix() {
        List<Book> results = bookRepository.searchBooks("Mar", null, null, null, null);

        assertThat(results)
            .extracting(Book::getAuthor)
            .containsExactlyInAnyOrder("Martin Fowler", "Martin Kleppmann");
    }

    @Test
    void searchBooks_findsValidTwoCharacterGenrePrefix() {
        List<Book> results = bookRepository.searchBooks("Tech", null, null, null, null);

        assertThat(results)
            .extracting(Book::getGenre)
            .containsOnly("Technology");
    }

    @Test
    void searchBooks_matchesTextPrefixesCaseInsensitively() {
        List<Book> results = bookRepository.searchBooks("dEs", null, null, null, null);

        assertThat(results)
            .extracting(Book::getTitle)
            .containsExactly("Designing Data-Intensive Applications");
    }

    @Test
    void searchBooks_doesNotMatchMiddleOfWordText() {
        List<Book> results = bookRepository.searchBooks("ata", null, null, null, null);

        assertThat(results).isEmpty();
    }

    @Test
    void searchBooks_excludesUnrelatedResults() {
        List<Book> results = bookRepository.searchBooks("Ca", null, null, null, null);

        assertThat(results)
            .extracting(Book::getTitle)
            .doesNotContain(
                "Clean Code",
                "Designing Data-Intensive Applications",
                "Scala for the Impatient"
            );
    }

    @Test
    void searchBooks_findsIsbnPrefixMatches() {
        List<Book> results = bookRepository.searchBooks(null, "978-1-449", null, null, null);

        assertThat(results)
            .extracting(Book::getIsbn)
            .containsExactly("978-1-4493-6130-9");
    }

    @Test
    void searchBooks_doesNotMatchMiddleOfIsbn() {
        List<Book> results = bookRepository.searchBooks(null, "10000", null, null, null);

        assertThat(results).isEmpty();
    }

    @Test
    void searchBooks_returnsNoResultsWhenPrefixDoesNotExist() {
        List<Book> results = bookRepository.searchBooks("Xy", null, null, null, null);

        assertThat(results).isEmpty();
    }

    private Book book(String isbn, String title, String author, String genre) {
        return new Book(isbn, title, author, 2024, genre);
    }
}
