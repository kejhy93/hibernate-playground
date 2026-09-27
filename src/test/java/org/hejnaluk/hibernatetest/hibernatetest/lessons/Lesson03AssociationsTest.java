package org.hejnaluk.hibernatetest.hibernatetest.lessons;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Author;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Book;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Genre;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Review;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Owning vs inverse side, cascading and orphan removal.
 */
class Lesson03AssociationsTest extends HibernateLesson {

    @Test
    void persistCascadesToReviews() {
        Book book = new Book("Domain-Driven Design", "978-0321125217", Genre.TECHNICAL);
        book.addReview(new Review(5, "The blue book."));
        book.addReview(new Review(4, "Long, but worth it."));

        em.persist(book); // only the book is persisted explicitly
        em.flush();

        assertThat(stats.getEntityInsertCount()).isEqualTo(3); // book + 2 reviews
    }

    @Test
    void orphanRemovalDeletesReviewsRemovedFromTheCollection() {
        Book book = em.find(Book.class, 1L);
        Review first = book.getReviews().getFirst();

        book.removeReview(first);
        em.flush();

        assertThat(stats.getEntityDeleteCount()).isEqualTo(1);
        assertThat(em.find(Review.class, first.getId())).isNull();
    }

    @Test
    void onlyTheOwningSideIsWrittenToTheDatabase() {
        Book book = em.find(Book.class, 7L);
        Author fowler = em.find(Author.class, 3L);

        step("changing only the inverse side (Author.books, mappedBy) is ignored");
        fowler.getBooks().add(book);
        flushAndClear();
        assertThat(authorCountOf(7L)).isEqualTo(1);

        step("changing the owning side (Book.authors) inserts into book_author");
        book = em.find(Book.class, 7L);
        book.addAuthor(em.find(Author.class, 3L));
        flushAndClear();
        assertThat(authorCountOf(7L)).isEqualTo(2);
    }

    @Test
    void removingABookRemovesItsReviewsAndJoinTableRows() {
        Book book = em.find(Book.class, 4L);

        em.remove(book);
        em.flush();

        // Reviews go through cascade REMOVE; book_author rows are deleted because
        // Book owns that collection. No ON DELETE CASCADE needed in the schema.
        assertThat(stats.getEntityDeleteCount()).isEqualTo(3); // 2 reviews + the book
        assertThat(authorCountOf(4L)).isZero();
    }

    private long authorCountOf(long bookId) {
        return ((Number) em.createNativeQuery("select count(*) from book_author where book_id = :id")
                .setParameter("id", bookId)
                .getSingleResult()).longValue();
    }
}
