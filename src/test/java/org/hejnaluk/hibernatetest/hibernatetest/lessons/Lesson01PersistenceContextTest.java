package org.hejnaluk.hibernatetest.hibernatetest.lessons;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Book;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Genre;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The persistence context (first-level cache) and entity states.
 * <pre>
 *   new Book()                    em.persist()                     em.detach() / em.clear()
 *  ───────────► TRANSIENT ──────────────────────► MANAGED ─────────────────────────────► DETACHED
 *                                  em.find()        ▲      end of transaction / session       │
 *                                  query            └─────────────────────────────────────────┘
 *                                                                    em.merge() (returns a copy)
 * </pre>
 * The persistence context is a map of {@code (entity class, id) -> entity instance} plus a
 * snapshot of the values each entity had when it was loaded. In Spring it lives as long as the
 * {@code @Transactional} method, here as long as the test method.
 * <p>
 * Remember: {@code em.flush()} sends SQL to the database, but the test's transaction is
 * rolled back at the end, so nothing is committed.
 */
@DisplayName("Lesson 01: Persistence context and entity states")
class Lesson01PersistenceContextTest extends HibernateLesson {

    @Test
    @DisplayName("First-level cache: finding the same entity twice runs one SELECT and returns the same object")
    void findingTheSameEntityTwiceHitsTheDatabaseOnce() {
        // 1st find: (Book, 1) is not in the persistence context yet
        //   -> SELECT ... FROM book WHERE id = 1
        //   -> Hibernate creates the Book object, stores it in the persistence context
        //      together with a snapshot of the loaded column values (used later for dirty checking).
        Book first = em.find(Book.class, 1L);

        // 2nd find: (Book, 1) is already in the persistence context
        //   -> returned straight from memory, no SQL at all.
        Book second = em.find(Book.class, 1L);

        assertThat(statements()).isEqualTo(1);

        // Within one persistence context, one database row = exactly one Java object.
        // That's why you can safely compare managed entities of the same session with ==.
        // A different session (e.g. another transaction) would give you a different object.
        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("Dirty checking: modifying a managed entity is enough, no save() needed")
    void dirtyCheckingWritesChangesWithoutCallingSave() {
        // Managed entity + snapshot {title="1984", price=9.99, version=0, ...}
        Book book = em.find(Book.class, 1L);

        // Plain Java setter: nothing happens in the database yet, Hibernate doesn't even know.
        book.setTitle("Nineteen Eighty-Four");
        assertThat(stats.getEntityUpdateCount()).isZero();

        // flush() (also called automatically before commit):
        //   -> Hibernate walks over EVERY managed entity and compares current values with the snapshot
        //   -> title differs, so it issues:
        //        UPDATE book SET genre=?, isbn=?, ..., title=?, version=1 WHERE id=1 AND version=0
        //      (all columns are updated by default; @DynamicUpdate would send only the changed ones)
        //   -> "AND version=0" is the optimistic lock check from @Version
        //   -> the snapshot is refreshed, so a second flush would not update again
        step("flush: Hibernate compares every managed entity with its loaded snapshot");
        em.flush();

        assertThat(stats.getEntityUpdateCount()).isEqualTo(1);
        assertThat(book.getVersion()).isEqualTo(1L); // @Version was incremented in the Java object too
    }

    @Test
    @DisplayName("Dirty checking compares values: setting the same value does not produce an UPDATE")
    void settingTheSameValueIsNotAChange() {
        Book book = em.find(Book.class, 1L);

        // Hibernate doesn't track setter calls; it only compares values at flush time.
        // "1984".equals("1984") -> not dirty.
        book.setTitle("1984");

        em.flush();

        assertThat(stats.getEntityUpdateCount()).isZero();
        assertThat(book.getVersion()).isZero(); // no update -> no version bump
    }

    @Test
    @DisplayName("Detached entity: changes are no longer tracked and never reach the database")
    void changesToDetachedEntitiesAreIgnored() {
        Book book = em.find(Book.class, 1L);

        // detach() removes the entity (and its snapshot) from the persistence context.
        // In a real app this happens implicitly when the @Transactional method returns.
        em.detach(book);
        assertThat(em.contains(book)).isFalse();

        // Just a change to a Java object that Hibernate no longer knows about.
        book.setTitle("Changed while detached");

        // flush() only inspects MANAGED entities -> nothing to do.
        em.flush();
        assertThat(stats.getEntityUpdateCount()).isZero();

        // clear() empties the persistence context, so this find() runs a fresh SELECT
        // and proves the database still has the original title.
        em.clear();
        assertThat(em.find(Book.class, 1L).getTitle()).isEqualTo("1984");
    }

    @Test
    @DisplayName("merge(): copies a detached entity's state onto a managed instance and returns that instance")
    void mergeCopiesDetachedStateIntoAManagedInstance() {
        // SELECT #1: load the book, then detach it.
        // This simulates an entity that outlived its transaction, e.g. returned from a service
        // in one request and sent back in the next one.
        Book detached = em.find(Book.class, 1L);
        em.detach(detached);
        detached.setTitle("Merged title");

        // merge(detached) does roughly this:
        //   1. look up (Book, 1) in the persistence context -> not there
        //   2. SELECT #2: load the current row from the database -> a NEW managed instance + snapshot
        //   3. compare versions: detached.version (0) vs. database version (0)
        //      -> if they differed: OptimisticLockException (see Lesson 04)
        //   4. copy ALL fields from the detached object onto the managed one
        //      (including nulls! a partially filled object would wipe columns)
        //   5. return the managed instance
        // The UPDATE is not executed here; the managed copy is simply dirty now.
        Book managed = em.merge(detached);
        assertThat(statements()).isEqualTo(2);

        // merge() returns a DIFFERENT object. The argument stays detached, so any change
        // made to `detached` after this point is ignored. Always continue with the return value.
        assertThat(managed).isNotSameAs(detached);
        assertThat(em.contains(managed)).isTrue();
        assertThat(em.contains(detached)).isFalse();
        assertThat(managed.getTitle()).isEqualTo("Merged title");

        // flush(): regular dirty checking of the managed copy -> UPDATE ... WHERE id=1 AND version=0
        em.flush();
        assertThat(stats.getEntityUpdateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("persist(): the id is assigned immediately, but the INSERT waits until flush")
    void persistAssignsIdButInsertHappensAtFlush() {
        // Transient: an ordinary Java object, no id, unknown to Hibernate.
        Book book = new Book("Java Persistence with Hibernate", "978-1617290459", Genre.TECHNICAL);
        assertThat(book.getId()).isNull();
        assertThat(em.contains(book)).isFalse();

        // persist():
        //   -> gets an id from book_seq. With allocationSize = 50 the pooled optimizer calls
        //      "select nextval('book_seq')" only once per 50 ids, so often no SQL at all here.
        //   -> puts the entity into the persistence context (managed)
        //   -> queues an INSERT action; it is NOT executed yet
        em.persist(book);

        assertThat(book.getId()).isNotNull();
        assertThat(em.contains(book)).isTrue();
        assertThat(stats.getEntityInsertCount()).isZero();

        // Delaying the INSERT lets Hibernate batch several inserts together (see Lesson 04).
        // Note: with GenerationType.IDENTITY the id is generated BY the INSERT, so Hibernate
        // would have to execute the INSERT right inside persist(), which prevents batching.

        // flush(): the queued INSERT is executed.
        em.flush();
        assertThat(stats.getEntityInsertCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Auto-flush: a query flushes pending changes first so it sees up-to-date data")
    void queriesTriggerAnAutoFlushSoTheySeePendingChanges() {
        Book book = em.find(Book.class, 2L);

        // Only in memory so far: the database still says "Animal Farm".
        book.setTitle("Animal Farm: A Fairy Story");
        assertThat(stats.getEntityUpdateCount()).isZero();

        // The flush mode is AUTO by default. Before running a JPQL query, Hibernate checks
        // whether pending changes touch the tables the query reads (here: book).
        // They do -> it flushes the UPDATE first, then runs the SELECT.
        // Without this, the query would read the old title and return 0.
        // (With a query on another table, e.g. author, Hibernate would skip the flush.)
        step("JPQL query touching the book table: Hibernate flushes the pending UPDATE first");
        Long count = em.createQuery("select count(b) from Book b where b.title like 'Animal Farm:%'", Long.class)
                .getSingleResult();

        assertThat(count).isEqualTo(1);
        assertThat(stats.getEntityUpdateCount()).isEqualTo(1);
    }
}
