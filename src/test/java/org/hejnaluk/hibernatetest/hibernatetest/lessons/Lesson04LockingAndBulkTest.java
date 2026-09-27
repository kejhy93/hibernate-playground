package org.hejnaluk.hibernatetest.hibernatetest.lessons;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Book;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Genre;
import org.hejnaluk.hibernatetest.hibernatetest.repository.BookRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Optimistic locking with @Version, bulk updates and JDBC batching.
 */
class Lesson04LockingAndBulkTest extends HibernateLesson {

    @Autowired
    BookRepository bookRepository;

    @Test
    void mergingAStaleEntityFails() {
        step("user A loads the book and keeps it (e.g. in a web form)");
        Book staleCopy = em.find(Book.class, 3L);
        em.detach(staleCopy);

        step("meanwhile user B changes it: version 0 -> 1");
        Book current = em.find(Book.class, 3L);
        current.setPrice(new BigDecimal("12.99"));
        em.flush();

        step("user A submits changes based on version 0");
        staleCopy.setPrice(new BigDecimal("5.00"));
        assertThatThrownBy(() -> em.merge(staleCopy))
                .isInstanceOf(OptimisticLockException.class);
    }

    @Test
    void bulkUpdateBypassesThePersistenceContext() {
        Book book = em.find(Book.class, 4L);
        BigDecimal before = book.getPrice();

        int updated = bookRepository.bulkChangePrice(Genre.TECHNICAL, new BigDecimal("2"));
        assertThat(updated).isEqualTo(5);

        // The managed instance still has the old price: the UPDATE never went through it.
        assertThat(book.getPrice()).isEqualByComparingTo(before);

        em.refresh(book);
        assertThat(book.getPrice()).isEqualByComparingTo(before.multiply(new BigDecimal("2")));
        assertThat(book.getVersion()).isZero(); // bulk updates don't touch @Version
    }

    @Test
    void insertsAreSentInJdbcBatches() {
        IntStream.range(0, 45).forEach(i ->
                em.persist(new Book("Generated book " + i, "GEN-" + i, Genre.SCIENCE)));

        em.flush();

        // 45 INSERTs, but a single prepared statement executed as 3 batches (20 + 20 + 5).
        // The ids come from the pooled sequence: at most 1 call to book_seq per 50 ids
        // (zero if an earlier test in this JVM already reserved a block).
        // Look for "executing N JDBC batches" in the session metrics logged at the end.
        assertThat(stats.getEntityInsertCount()).isEqualTo(45);
        log.info("Prepared statements: {}", statements());
    }
}
