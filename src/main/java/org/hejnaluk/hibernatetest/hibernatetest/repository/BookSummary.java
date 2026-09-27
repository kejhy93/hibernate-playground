package org.hejnaluk.hibernatetest.hibernatetest.repository;

import java.math.BigDecimal;

/** DTO projection: fetched directly by a JPQL constructor expression, never a managed entity. */
public record BookSummary(Long id, String title, String publisherName, BigDecimal price, Double averageRating) {
}
