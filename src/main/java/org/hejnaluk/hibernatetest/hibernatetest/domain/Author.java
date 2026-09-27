package org.hejnaluk.hibernatetest.hibernatetest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "author_seq")
    @SequenceGenerator(name = "author_seq", sequenceName = "author_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    private Integer birthYear;

    // Inverse side of the many-to-many: Book.authors owns the join table.
    // Changes made only here are NOT written to book_author.
    @ManyToMany(mappedBy = "authors")
    private Set<Book> books = new HashSet<>();

    public Author(String name, Integer birthYear) {
        this.name = name;
        this.birthYear = birthYear;
    }

    // toString must not touch lazy collections, or logging an entity triggers queries
    // (or LazyInitializationException outside a transaction).
    @Override
    public String toString() {
        return "Author{id=" + id + ", name='" + name + "'}";
    }
}
