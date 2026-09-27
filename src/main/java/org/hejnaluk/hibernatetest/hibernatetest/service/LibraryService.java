package org.hejnaluk.hibernatetest.hibernatetest.service;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Book;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Review;
import org.hejnaluk.hibernatetest.hibernatetest.repository.BookRepository;
import org.hejnaluk.hibernatetest.hibernatetest.repository.BookSummary;
import org.hejnaluk.hibernatetest.hibernatetest.web.BookDto;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LibraryService {

    private final BookRepository bookRepository;

    /** N+1: one query for books, then one per publisher and one per book's authors collection. */
    public List<BookDto> listBooksNaive() {
        return bookRepository.findAll().stream().map(BookDto::from).toList();
    }

    /** Same result as listBooksNaive(), but in a single query thanks to a fetch join. */
    public List<BookDto> listBooksWithFetchJoin() {
        return bookRepository.findAllWithPublisherAndAuthors().stream().map(BookDto::from).toList();
    }

    /** DTO projection: no entities, no dirty checking, only the columns needed. */
    public List<BookSummary> listSummaries() {
        return bookRepository.findSummaries();
    }

    public BookDto getBook(Long id) {
        return BookDto.from(findBook(id));
    }

    /** No save() call: the book is managed, so dirty checking issues the UPDATE on commit. */
    @Transactional
    public BookDto changePrice(Long id, BigDecimal newPrice) {
        Book book = findBook(id);
        book.setPrice(newPrice);
        return BookDto.from(book);
    }

    /** The new review is inserted through CascadeType.ALL on Book.reviews. */
    @Transactional
    public BookDto addReview(Long bookId, int rating, String comment) {
        Book book = findBook(bookId);
        book.addReview(new Review(rating, comment));
        return BookDto.from(book);
    }

    private Book findBook(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book " + id + " not found"));
    }
}
