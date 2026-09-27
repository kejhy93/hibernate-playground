package org.hejnaluk.hibernatetest.hibernatetest.repository;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublisherRepository extends JpaRepository<Publisher, Long> {
}
