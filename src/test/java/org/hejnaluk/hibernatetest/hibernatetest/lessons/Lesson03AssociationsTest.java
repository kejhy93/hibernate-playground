package org.hejnaluk.hibernatetest.hibernatetest.lessons;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Author;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Book;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Genre;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Review;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Owning vs inverse side, cascading and orphan removal.
 * <p>
 * Every bidirectional association has two Java fields but only ONE database representation
 * (a foreign key column or a join table). Hibernate needs to know which field to read when
 * writing SQL:
 * <ul>
 *   <li><b>Owning side</b>: the field WITHOUT {@code mappedBy}. Its value is written to the database.
 *       <ul>
 *         <li>{@code Review.book} owns the review.book_id column</li>
 *         <li>{@code Book.authors} owns the book_author join table</li>
 *       </ul></li>
 *   <li><b>Inverse side</b>: the field WITH {@code mappedBy}. It's a read-only mirror for Hibernate:
 *       loaded from the database, but changes to it are never written.
 *       <ul>
 *         <li>{@code Book.reviews} (mappedBy = "book")</li>
 *         <li>{@code Author.books} (mappedBy = "authors")</li>
 *       </ul></li>
 * </ul>
 * The helper methods {@code Book.addReview/removeReview/addAuthor/removeAuthor} update BOTH sides,
 * so the owning side is always set and the Java objects stay consistent within the session.
 * <p>
 * Cascading is a separate concept: it decides which {@code EntityManager} operations
 * (persist, merge, remove, ...) are passed on from a parent to its associated entities.
 */
@DisplayName("Lesson 03: Associations: owning side, cascading and orphan removal")
class Lesson03AssociationsTest extends HibernateLesson {

    @Test
    @DisplayName("Cascade PERSIST: persisting a book also persists the new reviews in its collection")
    void persistCascadesToReviews() {
        // Three transient objects. addReview() adds the review to book.reviews (inverse side)
        // AND sets review.book (owning side, which becomes review.book_id).
        // If you only did book.getReviews().add(review), review.book_id would be NULL on insert
        // and the NOT NULL constraint would fail.
        Book book = new Book("Domain-Driven Design", "978-0321125217", Genre.TECHNICAL);
        Review blueBook = new Review(5, "The blue book.");
        Review longRead = new Review(4, "Long, but worth it.");
        book.addReview(blueBook);
        book.addReview(longRead);

        // Only the book is persisted explicitly. Book.reviews has cascade = ALL (includes PERSIST),
        // so Hibernate walks the collection and persists each review too.
        // All three become managed and get ids, but no INSERT runs yet.
        em.persist(book);
        assertThat(blueBook.getId()).isNotNull();
        assertThat(em.contains(longRead)).isTrue();
        assertThat(stats.getEntityInsertCount()).isZero();

        // flush(): INSERT book first, then the reviews, because the reviews reference the book
        // through a foreign key. With order_inserts=true the two review INSERTs go in one JDBC batch.
        // Without cascade you would have to call em.persist(review) for every review yourself.
        em.flush();

        assertThat(stats.getEntityInsertCount()).isEqualTo(3); // book + 2 reviews
    }

    @Test
    @DisplayName("orphanRemoval: a review removed from the book's collection is deleted from the database")
    void orphanRemovalDeletesReviewsRemovedFromTheCollection() {
        // SELECT book 1. Reviews are lazy: getFirst() initializes the collection with
        // SELECT ... FROM review WHERE book_id=1
        Book book = em.find(Book.class, 1L);
        Review first = book.getReviews().getFirst();

        // removeReview() removes it from book.reviews AND sets review.book = null.
        // Still only in memory.
        book.removeReview(first);

        // flush(): Hibernate notices that an element disappeared from a collection with
        // orphanRemoval = true -> the review is an "orphan" -> DELETE FROM review WHERE id=?
        //
        // Without orphanRemoval: dirty checking would see review.book changed to null and run
        // UPDATE review SET book_id=NULL ..., which fails on the NOT NULL constraint.
        // (Orphan removal makes sense only when the child cannot exist without its parent.)
        em.flush();

        assertThat(stats.getEntityDeleteCount()).isEqualTo(1);
        assertThat(em.find(Review.class, first.getId())).isNull();

        // Compare with cascade REMOVE (also part of cascade = ALL):
        //   cascade REMOVE -> em.remove(parent) also removes the children (see the last test)
        //   orphanRemoval  -> removing a child from the parent's collection deletes the child
    }

    @Test
    @DisplayName("Owning side: only changes to Book.authors are written, Author.books (mappedBy) is ignored")
    void onlyTheOwningSideIsWrittenToTheDatabase() {
        // Book 7 (High-Performance Java Persistence) has 1 author: Vlad Mihalcea.
        // Author 3 ( Martin Fowler ) has 2 books: Refactoring and Patterns of Enterprise Application Architecture
        Book book = em.find(Book.class, 7L);
        Author fowler = em.find(Author.class, 3L);

        assertThat(book.getAuthors().size()).isEqualTo(1);
        assertThat(fowler.getBooks().size()).isEqualTo(2);

        // Author.books is the INVERSE side (mappedBy = "authors").
        // add() initializes the collection (SELECT) and adds the book in memory...
        // ...and at flush Hibernate ignores it: no INSERT INTO book_author.
        step("changing only the inverse side (Author.books, mappedBy) is ignored");
        fowler.getBooks().add(book);
        flushAndClear();
        assertThat(authorCountOf(7L)).isEqualTo(1);

        // Book.authors is the OWNING side. addAuthor() changes it (and Author.books, for consistency).
        // At flush Hibernate compares the collection with its snapshot, finds one new element and runs
        //   INSERT INTO book_author (book_id, author_id) VALUES (7, 3)
        // Only the new row is inserted because it's a Set. With a List (a "bag", no index column)
        // Hibernate can't identify single rows and would DELETE all rows of book 7, then re-insert them.
        step("changing the owning side (Book.authors) inserts into book_author");
        book = em.find(Book.class, 7L);
        book.addAuthor(em.find(Author.class, 3L));
        flushAndClear();
        assertThat(authorCountOf(7L)).isEqualTo(2);

        // A change to a collection the entity OWNS counts as a change of the entity itself:
        // Hibernate incremented Book's @Version, even though no book column changed.
        // (@OptimisticLock(excluded = true) on the collection turns this off.)
        assertThat(em.find(Book.class, 7L).getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Cascade REMOVE: deleting a book deletes its reviews and join table rows, but not its authors")
    void removingABookRemovesItsReviewsAndJoinTableRows() {
        // Book 4 (Refactoring): 2 reviews, 2 authors (Fowler and Beck).
        Book book = em.find(Book.class, 4L);

        // remove() schedules the book for deletion. Because Book.reviews has cascade REMOVE,
        // Hibernate first loads the reviews (SELECT ... FROM review WHERE book_id=4) and schedules
        // each of them for deletion too. Nothing is executed yet.
        em.remove(book);
        assertThat(em.contains(book)).isFalse(); // the book is now in the "removed" state

        // flush() executes, in an order that respects the foreign keys:
        //   DELETE FROM book_author WHERE book_id=4   <- Book OWNS the authors collection, so Hibernate
        //                                               cleans up the join table itself
        //   DELETE FROM review WHERE id=?  (x2)       <- cascade REMOVE, children before the parent
        //   DELETE FROM book WHERE id=4 AND version=0 <- @Version is checked on delete too
        // No ON DELETE CASCADE is needed in the schema.
        em.flush();

        assertThat(stats.getEntityDeleteCount()).isEqualTo(3); // 2 reviews + the book
        assertThat(authorCountOf(4L)).isZero();

        // The authors survive: Book.authors has NO cascade. Never put CascadeType.REMOVE (or ALL)
        // on a @ManyToMany: deleting a book would delete Kent Beck, who also wrote book 5.
        assertThat(em.find(Author.class, 3L)).isNotNull();
        assertThat(em.find(Author.class, 4L)).isNotNull();
    }

    /** Reads the join table directly with SQL, bypassing the persistence context. */
    private long authorCountOf(long bookId) {
        return ((Number)
                em.createNativeQuery("select count(*) from book_author where book_id = :id")
                .setParameter("id", bookId)
                .getSingleResult())
                .longValue();
    }
}
