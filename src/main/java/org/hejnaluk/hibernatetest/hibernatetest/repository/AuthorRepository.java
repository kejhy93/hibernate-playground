package org.hejnaluk.hibernatetest.hibernatetest.repository;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Author;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    Optional<Author> findByName(String name);
}
