package com.tus.microservices.library.service;

import com.tus.microservices.library.dto.BookDTO;
import com.tus.microservices.library.entity.Book;
import com.tus.microservices.library.repository.BookRepository;
import com.tus.microservices.library.repository.LibraryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private BookService bookService;

    @Test
    void searchBooks_doesNotTriggerTextSearchForShortTextInput() {
        List<BookDTO> results = bookService.searchBooks("C", null, null, null);

        assertThat(results).isEmpty();
        verifyNoInteractions(bookRepository, libraryRepository, inventoryClient);
    }

    @Test
    void searchBooks_trimsAndUsesIsbnPrefixQueries() {
        Book book = new Book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 2008, "Technology");
        when(bookRepository.searchBooks(null, "978", null, null, null)).thenReturn(List.of(book));

        List<BookDTO> results = bookService.searchBooks(" 978 ", null, null, null);

        assertThat(results)
            .extracting(BookDTO::isbn)
            .containsExactly("978-0-13-468599-1");
        verify(bookRepository).searchBooks(null, "978", null, null, null);
    }
}
