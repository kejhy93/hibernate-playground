package org.hejnaluk.hibernatetest.hibernatetest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "book_seq")
    @SequenceGenerator(name = "book_seq", sequenceName = "book_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    // Natural id: unique business key, usable with session.bySimpleNaturalId(Book.class)
    @org.hibernate.annotations.NaturalId
    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Genre genre;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private LocalDate publishedOn;

    // @ManyToOne defaults to EAGER in JPA. Making it LAZY is almost always what you want.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id")
    private Publisher publisher;

    // Owning side of the many-to-many (no mappedBy): this collection drives book_author rows.
    // A Set avoids Hibernate's "delete all + re-insert" behaviour that a List (bag) gets.
    @ManyToMany
    @JoinTable(name = "book_author",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id"))
    private Set<Author> authors = new HashSet<>();

    // Inverse side: Review.book owns the foreign key. Reviews live and die with their book.
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Review> reviews = new ArrayList<>();

    // Optimistic locking: Hibernate adds "where version = ?" to every UPDATE/DELETE
    // and increments it. A stale update fails with OptimisticLockException.
    @Version
    private Long version;

    public Book(String title, String isbn, Genre genre) {
        this.title = title;
        this.isbn = isbn;
        this.genre = genre;
    }

    // Helper methods keep both sides of bidirectional associations in sync in memory.

    public void addAuthor(Author author) {
        authors.add(author);
        author.getBooks().add(this);
    }

    public void removeAuthor(Author author) {
        authors.remove(author);
        author.getBooks().remove(this);
    }

    public void addReview(Review review) {
        reviews.add(review);
        review.setBook(this);
    }

    public void removeReview(Review review) {
        reviews.remove(review);
        review.setBook(null);
    }

    // Identity based on the natural id: stable across all entity states (transient, managed,
    // detached), unlike the generated id, which is null until persist.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book other)) return false;
        return isbn != null && isbn.equals(other.getIsbn());
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(isbn);
    }

    @Override
    public String toString() {
        return "Book{id=" + id + ", title='" + title + "', version=" + version + "}";
    }
}
