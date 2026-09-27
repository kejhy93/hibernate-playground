-- Seed data uses ids below the sequences' START WITH 1000, so new rows never collide.
INSERT INTO publisher (id, name) VALUES
    (1, 'Penguin'),
    (2, 'O''Reilly'),
    (3, 'Addison-Wesley');

INSERT INTO author (id, name, birth_year) VALUES
    (1, 'George Orwell', 1903),
    (2, 'Aldous Huxley', 1894),
    (3, 'Martin Fowler', 1963),
    (4, 'Kent Beck', 1961),
    (5, 'Erich Gamma', 1961),
    (6, 'Richard Helm', 1958),
    (7, 'Vlad Mihalcea', 1983);

INSERT INTO book (id, title, isbn, genre, price, published_on, publisher_id) VALUES
    (1, '1984',                                   '978-0451524935', 'FICTION',   9.99, '1949-06-08', 1),
    (2, 'Animal Farm',                            '978-0451526342', 'FICTION',   7.99, '1945-08-17', 1),
    (3, 'Brave New World',                        '978-0060850524', 'FICTION',  11.49, '1932-01-01', 1),
    (4, 'Refactoring',                            '978-0134757599', 'TECHNICAL', 47.99, '2018-11-20', 3),
    (5, 'Test Driven Development: By Example',    '978-0321146533', 'TECHNICAL', 39.99, '2002-11-08', 3),
    (6, 'Design Patterns',                        '978-0201633610', 'TECHNICAL', 54.99, '1994-10-31', 3),
    (7, 'High-Performance Java Persistence',      '978-9730228236', 'TECHNICAL', 34.99, '2016-10-12', NULL),
    (8, 'Patterns of Enterprise Application Architecture', '978-0321127426', 'TECHNICAL', 59.99, '2002-11-05', 3);

INSERT INTO book_author (book_id, author_id) VALUES
    (1, 1), (2, 1), (3, 2),
    (4, 3), (4, 4),
    (5, 4),
    (6, 5), (6, 6),
    (7, 7),
    (8, 3);

INSERT INTO review (id, book_id, rating, comment) VALUES
    (1, 1, 5, 'Chilling and still relevant.'),
    (2, 1, 4, 'Heavy but worth it.'),
    (3, 2, 5, 'Short and sharp.'),
    (4, 3, 4, 'Interesting counterpoint to 1984.'),
    (5, 4, 5, 'Every developer should read this.'),
    (6, 4, 4, 'Great catalog of refactorings.'),
    (7, 5, 3, 'Good, but a bit dated.'),
    (8, 6, 5, 'Classic.'),
    (9, 7, 5, 'Explains exactly what Hibernate does under the hood.'),
    (10, 7, 5, 'Must-read for JPA users.'),
    (11, 8, 4, 'Dense but foundational.');
