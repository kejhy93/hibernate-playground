package org.hejnaluk.hibernatetest.hibernatetest.repository;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}
