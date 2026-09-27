package org.hejnaluk.hibernatetest.hibernatetest.lessons;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Book;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Genre;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The persistence context (first-level cache) and entity states:
 * transient -> managed (persist/find) -> detached (clear/detach/close) -> managed again (merge).
 */
class Lesson01PersistenceContextTest extends HibernateLesson {

    @Test
    void findingTheSameEntityTwiceHitsTheDatabaseOnce() {
        Book first = em.find(Book.class, 1L);
        Book second = em.find(Book.class, 1L);

        // One SELECT; the second find is served from the persistence context.
        assertThat(statements()).isEqualTo(1);
        // Within one persistence context, one row = one Java object.
        assertThat(first).isSameAs(second);
    }

    @Test
    void dirtyCheckingWritesChangesWithoutCallingSave() {
        Book book = em.find(Book.class, 1L);
        book.setTitle("Nineteen Eighty-Four");

        step("flush: Hibernate compares every managed entity with its loaded snapshot");
        em.flush();

        assertThat(stats.getEntityUpdateCount()).isEqualTo(1);
        assertThat(book.getVersion()).isEqualTo(1L); // @Version was incremented
    }

    @Test
    void settingTheSameValueIsNotAChange() {
        Book book = em.find(Book.class, 1L);
        book.setTitle("1984"); // same as the current value

        em.flush();

        assertThat(stats.getEntityUpdateCount()).isZero();
    }

    @Test
    void changesToDetachedEntitiesAreIgnored() {
        Book book = em.find(Book.class, 1L);
        em.detach(book);
        book.setTitle("Changed while detached");

        em.flush();
        assertThat(stats.getEntityUpdateCount()).isZero();

        em.clear();
        assertThat(em.find(Book.class, 1L).getTitle()).isEqualTo("1984");
    }

    @Test
    void mergeCopiesDetachedStateIntoAManagedInstance() {
        Book detached = em.find(Book.class, 1L);
        em.detach(detached);
        detached.setTitle("Merged title");

        Book managed = em.merge(detached);

        // merge() returns a DIFFERENT, managed object. The argument stays detached.
        assertThat(managed).isNotSameAs(detached);
        assertThat(em.contains(managed)).isTrue();
        assertThat(em.contains(detached)).isFalse();

        em.flush();
        assertThat(stats.getEntityUpdateCount()).isEqualTo(1);
    }

    @Test
    void persistAssignsIdButInsertHappensAtFlush() {
        Book book = new Book("Java Persistence with Hibernate", "978-1617290459", Genre.TECHNICAL);
        assertThat(em.contains(book)).isFalse(); // transient

        em.persist(book);

        // The id comes from book_seq right away (SEQUENCE strategy), but no INSERT yet.
        assertThat(book.getId()).isNotNull();
        assertThat(em.contains(book)).isTrue(); // managed
        assertThat(stats.getEntityInsertCount()).isZero();

        em.flush();
        assertThat(stats.getEntityInsertCount()).isEqualTo(1);
    }

    @Test
    void queriesTriggerAnAutoFlushSoTheySeePendingChanges() {
        Book book = em.find(Book.class, 2L);
        book.setTitle("Animal Farm: A Fairy Story");

        step("JPQL query touching the book table: Hibernate flushes the pending UPDATE first");
        Long count = em.createQuery("select count(b) from Book b where b.title like 'Animal Farm:%'", Long.class)
                .getSingleResult();

        assertThat(count).isEqualTo(1);
        assertThat(stats.getEntityUpdateCount()).isEqualTo(1);
    }
}
