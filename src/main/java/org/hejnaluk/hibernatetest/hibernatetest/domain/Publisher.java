package org.hejnaluk.hibernatetest.hibernatetest.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Publisher {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "publisher_seq")
    @SequenceGenerator(name = "publisher_seq", sequenceName = "publisher_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    // No @OneToMany back to Book on purpose: a unidirectional @ManyToOne from Book is
    // often all you need, and it keeps Publisher cheap to load.

    public Publisher(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Publisher{id=" + id + ", name='" + name + "'}";
    }
}
