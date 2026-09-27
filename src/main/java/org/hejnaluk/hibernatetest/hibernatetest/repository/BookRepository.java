package org.hejnaluk.hibernatetest.hibernatetest.repository;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Book;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Genre;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    // Derived query: Spring Data generates "select b from Book b where b.isbn = ?1"
    Optional<Book> findByIsbn(String isbn);

    // Fetch join: publisher and authors are loaded in the same SQL statement.
    @Query("""
            select b from Book b
            left join fetch b.publisher
            left join fetch b.authors
            order by b.id
            """)
    List<Book> findAllWithPublisherAndAuthors();

    // Same idea as a fetch join, but declared as an entity graph.
    @EntityGraph(attributePaths = {"publisher", "authors"})
    List<Book> findByGenreOrderByTitle(Genre genre);

    // DTO projection: one query, returns plain records instead of managed entities.
    @Query("""
            select new org.hejnaluk.hibernatetest.hibernatetest.repository.BookSummary(
                b.id, b.title, p.name, b.price, avg(r.rating))
            from Book b
            left join b.publisher p
            left join b.reviews r
            group by b.id, b.title, p.name, b.price
            order by b.id
            """)
    List<BookSummary> findSummaries();

    // Bulk update: runs straight in the database, bypassing the persistence context.
    // Managed Book instances will NOT see the new price, and @Version is not incremented.
    @Modifying
    @Query("update Book b set b.price = b.price * :factor where b.genre = :genre")
    int bulkChangePrice(Genre genre, BigDecimal factor);
}
