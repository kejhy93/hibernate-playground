package org.hejnaluk.hibernatetest.hibernatetest.lessons;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Book;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Genre;
import org.hejnaluk.hibernatetest.hibernatetest.repository.BookRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Optimistic locking with @Version, bulk updates and JDBC batching.
 * <p>
 * <b>Optimistic locking</b>: instead of locking rows while a user works (pessimistic locking,
 * {@code SELECT ... FOR UPDATE}), each row carries a version number. Every UPDATE/DELETE Hibernate
 * issues for {@link Book} looks like
 * <pre>
 *   UPDATE book SET ..., version = 1 WHERE id = ? AND version = 0
 * </pre>
 * If someone else changed the row in the meantime, the version no longer matches, 0 rows are
 * updated, and Hibernate throws {@link OptimisticLockException} instead of silently overwriting
 * their change ("lost update").
 * <p>
 * <b>Bulk operations</b> ({@code update/delete ... where ...} in JPQL) and <b>batching</b> are the
 * two tools for changing many rows efficiently. They work very differently from the
 * entity-by-entity approach in the previous lessons.
 */
@DisplayName("Lesson 04: Optimistic locking, bulk updates and JDBC batching")
class Lesson04LockingAndBulkTest extends HibernateLesson {

    @Autowired
    BookRepository bookRepository;

    @Test
    @DisplayName("Optimistic locking: merging an entity with an outdated @Version throws OptimisticLockException")
    void mergingAStaleEntityFails() {
        // SELECT book 3 -> version 0. Detaching simulates user A keeping the book outside
        // a transaction, e.g. data shown in a web form that is submitted minutes later.
        step("user A loads the book and keeps it (e.g. in a web form)");
        Book staleCopy = em.find(Book.class, 3L);
        em.detach(staleCopy);

        // Book 3 is no longer in the persistence context -> another SELECT, a new managed instance.
        // flush(): UPDATE book SET price=12.99, version=1 WHERE id=3 AND version=0 -> 1 row updated.
        // In real life this would be user B's own transaction, committed in between.
        step("meanwhile user B changes it: version 0 -> 1");
        Book current = em.find(Book.class, 3L);
        current.setPrice(new BigDecimal("12.99"));
        em.flush();
        assertThat(current.getVersion()).isEqualTo(1L);
        long statementsBeforeMerge = statements();

        // merge(staleCopy): book 3 IS in the persistence context now (user B's managed instance),
        // so no SELECT is needed. Hibernate compares versions: staleCopy has 0, the managed one 1.
        // They differ -> the detached data is outdated -> OptimisticLockException, and no SQL is sent.
        // Without @Version, merge would copy price 5.00 over 12.99 and user B's change would be lost.
        step("user A submits changes based on version 0");
        staleCopy.setPrice(new BigDecimal("5.00"));
        assertThatThrownBy(() -> em.merge(staleCopy))
                .isInstanceOf(OptimisticLockException.class);
        assertThat(statements()).isEqualTo(statementsBeforeMerge);

        // The other way to hit the conflict: two transactions both load version 0 and both flush.
        // The second "UPDATE ... WHERE id=3 AND version=0" updates 0 rows, and Hibernate throws at
        // flush/commit. Through Spring (repositories, @Transactional) you get it wrapped as
        // ObjectOptimisticLockingFailureException.
        //
        // What to do with it: tell the user "someone else changed this, reload", or reload
        // and retry the operation if it's safe to repeat.
        // Also note: the exception marks the transaction rollback-only; this session can't commit anymore.
    }

    @Test
    @DisplayName("Bulk update: one SQL UPDATE for many rows, bypassing the persistence context and @Version")
    void bulkUpdateBypassesThePersistenceContext() {
        // Book 4 is managed, price 47.99, version 0.
        Book book = em.find(Book.class, 4L);
        BigDecimal before = book.getPrice();

        // JPQL: update Book b set b.price = b.price * :factor where b.genre = :genre
        // SQL:  UPDATE book SET price = price * 2 WHERE genre = 'TECHNICAL'
        // One statement for all 5 technical books; no entities are loaded, no dirty checking.
        // (Before running it, Hibernate auto-flushes pending changes to the book table, as in Lesson 01.)
        int updated = bookRepository.bulkChangePrice(Genre.TECHNICAL, new BigDecimal("2"));
        assertThat(updated).isEqualTo(5);
        assertThat(stats.getEntityUpdateCount()).isZero(); // no entity went through the normal update path

        // The persistence context doesn't know about it. The managed book still holds the old
        // price (and so does its snapshot). The database and memory now disagree.
        assertThat(book.getPrice()).isEqualByComparingTo(before);

        // Danger: if you now changed e.g. book.setTitle(...) and flushed, Hibernate's full-row UPDATE
        // would write the OLD price back and silently undo the bulk update.
        // Solutions:
        //   - run bulk operations before loading entities, or in their own transaction
        //   - @Modifying(clearAutomatically = true) to clear the persistence context afterwards
        //   - em.refresh(entity) to reload specific entities, as done here
        em.refresh(book); // SELECT ... FROM book WHERE id=4, overwriting the in-memory state
        assertThat(book.getPrice()).isEqualByComparingTo(before.multiply(new BigDecimal("2")));

        // The bulk UPDATE didn't touch the version column, so optimistic locking can't detect it:
        // a user holding version 0 can still overwrite the new price. If that matters, increment
        // it yourself ("set b.version = b.version + 1") or use Hibernate's "update versioned Book ...".
        // Bulk operations also skip cascades, lifecycle callbacks (@PreUpdate) and entity listeners.
        assertThat(book.getVersion()).isZero();
    }

    @Test
    @DisplayName("JDBC batching: 45 INSERTs are sent to the database in 3 round-trips instead of 45")
    void insertsAreSentInJdbcBatches() {
        // Each persist():
        //   - takes the next id from the pooled book_seq optimizer (one DB call per 50 ids)
        //   - queues an INSERT action; nothing is sent yet (see Lesson 01)
        IntStream.range(0, 45).forEach(i ->
                em.persist(new Book("Generated book " + i, "GEN-" + i, Genre.SCIENCE)));
        assertThat(stats.getEntityInsertCount()).isZero();

        // flush(): all 45 queued INSERTs have the same SQL, so with hibernate.jdbc.batch_size=20
        // Hibernate prepares the statement ONCE and uses JDBC addBatch()/executeBatch():
        //   batch 1: 20 rows, batch 2: 20 rows, batch 3: 5 rows -> 3 network round-trips instead of 45.
        // order_inserts=true sorts queued inserts by entity type, so interleaved Book/Review
        // inserts still form long batches instead of breaking the batch at every switch.
        em.flush();

        // Look for "executing 3 JDBC batches" in the session metrics logged at the end of the test.
        assertThat(stats.getEntityInsertCount()).isEqualTo(45);
        log.info("Prepared statements: {}", statements());

        // 1 prepared INSERT + at most 1 sequence call. The sequence call is skipped when an earlier
        // test in this JVM already reserved a block of 50 ids.
        assertThat(statements()).isLessThanOrEqualTo(2);

        // Batching stops working when:
        //   - ids come from GenerationType.IDENTITY: the INSERT must run inside persist() to get the id
        //   - batch_size is not set (the default is no batching)
        // For really big imports also call em.flush(); em.clear(); every batch_size entities,
        // otherwise the persistence context (and dirty checking at each flush) keeps growing.
        // The Postgres driver option reWriteBatchedInserts=true can go further and rewrite each batch
        // into a single multi-row INSERT ... VALUES (...), (...), ...
    }
}
