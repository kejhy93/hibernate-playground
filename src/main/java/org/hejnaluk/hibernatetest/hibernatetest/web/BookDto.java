package org.hejnaluk.hibernatetest.hibernatetest.web;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Author;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Book;

import java.math.BigDecimal;
import java.util.List;

public record BookDto(Long id, String title, String isbn, String genre, BigDecimal price,
                      String publisher, List<String> authors, Long version) {

    // Touches the lazy publisher and authors, so it must run inside a transaction.
    public static BookDto from(Book book) {
        return new BookDto(
                book.getId(),
                book.getTitle(),
                book.getIsbn(),
                book.getGenre().name(),
                book.getPrice(),
                book.getPublisher() == null ? null : book.getPublisher().getName(),
                book.getAuthors().stream().map(Author::getName).sorted().toList(),
                book.getVersion());
    }
}
