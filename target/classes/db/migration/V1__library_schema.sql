-- Sequences use INCREMENT BY 50 to match the entities' allocationSize = 50
-- (Hibernate's pooled optimizer hands out 50 ids per database round-trip).
CREATE SEQUENCE publisher_seq START WITH 1000 INCREMENT BY 50;
CREATE SEQUENCE author_seq    START WITH 1000 INCREMENT BY 50;
CREATE SEQUENCE book_seq      START WITH 1000 INCREMENT BY 50;
CREATE SEQUENCE review_seq    START WITH 1000 INCREMENT BY 50;

CREATE TABLE publisher (
    id   BIGINT       PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE
);

CREATE TABLE author (
    id        BIGINT       PRIMARY KEY,
    name      VARCHAR(200) NOT NULL,
    birth_year INTEGER
);

CREATE TABLE book (
    id           BIGINT        PRIMARY KEY,
    title        VARCHAR(300)  NOT NULL,
    isbn         VARCHAR(20)   NOT NULL UNIQUE,
    genre        VARCHAR(30)   NOT NULL,
    price        NUMERIC(10, 2),
    published_on DATE,
    publisher_id BIGINT        REFERENCES publisher (id),
    version      BIGINT        NOT NULL DEFAULT 0
);

-- Join table for the Book <-> Author many-to-many
CREATE TABLE book_author (
    book_id   BIGINT NOT NULL REFERENCES book (id),
    author_id BIGINT NOT NULL REFERENCES author (id),
    PRIMARY KEY (book_id, author_id)
);

CREATE TABLE review (
    id       BIGINT        PRIMARY KEY,
    book_id  BIGINT        NOT NULL REFERENCES book (id),
    rating   INTEGER       NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment  VARCHAR(2000),
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_book_publisher ON book (publisher_id);
CREATE INDEX idx_review_book    ON review (book_id);
