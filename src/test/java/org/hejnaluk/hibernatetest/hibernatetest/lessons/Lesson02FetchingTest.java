package org.hejnaluk.hibernatetest.hibernatetest.lessons;

import org.hejnaluk.hibernatetest.hibernatetest.domain.Book;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Genre;
import org.hejnaluk.hibernatetest.hibernatetest.domain.Publisher;
import org.hejnaluk.hibernatetest.hibernatetest.repository.BookRepository;
import org.hejnaluk.hibernatetest.hibernatetest.repository.BookSummary;
import org.hejnaluk.hibernatetest.hibernatetest.web.BookDto;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lazy loading, proxies, the N+1 problem and the ways to avoid it.
 * <p>
 * Mapping recap (see {@link Book}):
 * <ul>
 *   <li>{@code Book.publisher}: {@code @ManyToOne(fetch = LAZY)}. Loading a book gives you a
 *       <b>proxy</b> of the publisher that only knows its id (taken from the publisher_id column).</li>
 *   <li>{@code Book.authors}: {@code @ManyToMany}, LAZY by default for collections. Loading a book
 *       gives you an empty <b>PersistentSet</b> wrapper that loads its content on first access.</li>
 * </ul>
 * Lazy means "load when first touched". That's great when you don't need the data, and a
 * performance trap (N+1) when you do need it for many entities.
 * <p>
 * Seed data: 8 books, publishers 1 (3 books) and 3 (4 books), book 7 has no publisher;
 * books 4 and 6 have two authors, the others one.
 */
@DisplayName("Lesson 02: Fetching, lazy loading and the N+1 problem")
class Lesson02FetchingTest extends HibernateLesson {

    @Autowired
    BookRepository bookRepository;

    @BeforeEach
    void startWithEmptyPersistenceContext() {
        // Make sure every test really hits the database instead of reusing already loaded entities.
        flushAndClear();
    }

    @Test
    @DisplayName("N+1: one query for the list, then one extra query per lazy association touched")
    void nPlusOneQueries() {
        // The "1": SELECT b.* FROM book b
        // Publishers and authors are NOT loaded. Each book gets a publisher proxy (id only)
        // and an uninitialized authors collection.
        step("1 query for the books...");
        List<Book> books = bookRepository.findAll();
        assertThat(statements()).isEqualTo(1);

        // The "N": BookDto.from() calls book.getPublisher().getName() and book.getAuthors().stream().
        // For each book:
        //   - getName() on an uninitialized publisher proxy -> SELECT ... FROM publisher WHERE id=?
        //     BUT only once per publisher: all books of publisher 1 share the same proxy instance
        //     (one row = one object in the persistence context), so the 2nd book of Penguin is free.
        //   - authors.stream() on an uninitialized collection
        //     -> SELECT ... FROM book_author JOIN author ... WHERE book_id=?, one per book.
        step("...then 1 query per distinct publisher and 1 per book's authors collection");
        List<BookDto> dtos = books.stream().map(BookDto::from).toList();

        log.info("Loaded {} books with {} statements", dtos.size(), statements());
        // 1 (books) + 2 (publishers 1 and 3; book 7 has none) + 8 (authors of each book)
        // With 1000 books this would be ~1000 queries: each one fast, together very slow.
        //
        // Cheap mitigation without changing the query: hibernate.default_batch_fetch_size=N
        // (or @BatchSize) makes Hibernate load N lazy associations at once with WHERE id IN (...).
        // Try it: see PLAYGROUND.md, exercise 1.
        assertThat(statements()).isEqualTo(11);
    }

    @Test
    @DisplayName("Fix 1, JOIN FETCH: load books, publishers and authors in a single query")
    void fetchJoinLoadsEverythingInOneQuery() {
        // JPQL: select b from Book b left join fetch b.publisher left join fetch b.authors
        // SQL:  SELECT b.*, p.*, a.* FROM book b
        //         LEFT JOIN publisher p ON ...
        //         LEFT JOIN book_author ba ON ... LEFT JOIN author a ON ...
        // "fetch" means: use the joined columns to INITIALIZE the associations, not just to filter.
        // "left" keeps book 7, which has no publisher. An inner join would drop it.
        List<Book> books = bookRepository.findAllWithPublisherAndAuthors();

        // The SQL returns 10 rows (books 4 and 6 appear twice, once per author), but only 8 books.
        // Hibernate 6+ removes the duplicate root entities itself. In Hibernate 5 you needed
        // "select distinct b" for that.
        assertThat(books).hasSize(8);
        assertThat(Hibernate.isInitialized(books.getFirst().getAuthors())).isTrue();
        assertThat(Hibernate.isInitialized(books.getFirst().getPublisher())).isTrue();

        // Everything is already in memory, so mapping to DTOs runs no more SQL.
        books.forEach(BookDto::from);
        assertThat(statements()).isEqualTo(1);

        // Caveats of fetch joins:
        //  - fetching 2+ collections multiplies rows (Cartesian product); two List collections
        //    even fail with MultipleBagFetchException.
        //  - combining a collection fetch with pagination (setMaxResults / Pageable) makes Hibernate
        //    paginate IN MEMORY after loading all rows (warning HHH90003004).
    }

    @Test
    @DisplayName("Fix 2, @EntityGraph: same single query, declared as an annotation on the repository")
    void entityGraphLoadsEverythingInOneQuery() {
        // findByGenreOrderByTitle is a derived query with @EntityGraph(attributePaths = {"publisher", "authors"}).
        // Spring Data passes the graph as the "jakarta.persistence.fetchgraph" hint and Hibernate
        // adds the same LEFT JOINs as the fetch join above:
        //   SELECT ... FROM book b LEFT JOIN publisher ... LEFT JOIN book_author ... LEFT JOIN author ...
        //   WHERE b.genre = 'TECHNICAL' ORDER BY b.title
        // Handy because you can reuse one query with different graphs, and derived queries
        // can't contain "join fetch" at all.
        List<BookDto> dtos = bookRepository.findByGenreOrderByTitle(Genre.TECHNICAL).stream()
                .map(BookDto::from).toList();

        assertThat(dtos).hasSize(5);
        assertThat(statements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fix 3, DTO projection: select only the needed columns, no entities are loaded at all")
    void dtoProjectionLoadsNoEntitiesAtAll() {
        // JPQL constructor expression: select new ...BookSummary(b.id, b.title, p.name, b.price, avg(r.rating))
        // SQL:  SELECT b.id, b.title, p.name, b.price, avg(r.rating) FROM book b LEFT JOIN ... GROUP BY ...
        // Hibernate calls the record's constructor for every row. The results are plain Java objects:
        //   - not in the persistence context (no identity map, no snapshots -> less memory)
        //   - no dirty checking at flush, and changing them never updates anything
        //   - no lazy associations -> no N+1 and no LazyInitializationException, ever
        // Usually the best choice for read-only screens and REST responses.
        List<BookSummary> summaries = bookRepository.findSummaries();

        assertThat(summaries).hasSize(8);
        assertThat(statements()).isEqualTo(1);
        assertThat(stats.getEntityLoadCount()).isZero();
        summaries.forEach(s -> log.info("{}", s));
    }

    @Test
    @DisplayName("Lazy @ManyToOne: you get a proxy that knows only the id, the SELECT runs on first real use")
    void lazyManyToOneIsAProxy() {
        // SELECT ... FROM book WHERE id=4, which also reads publisher_id = 3.
        // Instead of loading publisher 3, Hibernate puts a proxy with id 3 into book.publisher.
        Book book = em.find(Book.class, 4L);
        Publisher publisher = book.getPublisher();

        // The proxy is a runtime-generated SUBCLASS of Publisher (Publisher$HibernateProxy,
        // generated by Byte Buddy; see the log line below). That's why lazy entities must not be final, and why
        // getClass() comparisons in equals() break with proxies (use instanceof or Hibernate.getClass()).
        assertThat(publisher).isInstanceOf(HibernateProxy.class);
        assertThat(publisher.getClass()).isNotEqualTo(Publisher.class);
        assertThat(Hibernate.isInitialized(publisher)).isFalse();
        log.info("Publisher class: {}", publisher.getClass().getName());

        // getId() is answered from the proxy itself: the id is all it knows, and all it needs.
        step("reading the id does not need a query: the proxy already has it");
        assertThat(publisher.getId()).isEqualTo(3L);
        assertThat(statements()).isEqualTo(1);

        // Any other getter initializes the proxy: SELECT ... FROM publisher WHERE id=3.
        // The proxy then delegates to the real Publisher object it loaded.
        step("reading any other property initializes the proxy");
        assertThat(publisher.getName()).isEqualTo("Addison-Wesley");
        assertThat(Hibernate.isInitialized(publisher)).isTrue();
        assertThat(statements()).isEqualTo(2);

        // Note: JPA's default for @ManyToOne/@OneToOne is EAGER. Without fetch = LAZY on
        // Book.publisher, em.find() would have joined the publisher right away, and
        // bookRepository.findAll() would run an extra SELECT per publisher immediately.
    }

    @Test
    @DisplayName("getReference(): returns a proxy without any SQL; the SELECT runs only if you read its state")
    void getReferenceDoesNotQueryUntilYouUseIt() {
        // Book 1 is not in the persistence context -> Hibernate returns a proxy with id 1.
        // It does NOT check that the row exists. If it doesn't, you get EntityNotFoundException
        // later, when the proxy is initialized.
        Book reference = em.getReference(Book.class, 1L);
        assertThat(statements()).isZero();
        assertThat(Hibernate.isInitialized(reference)).isFalse();
        step("no sql for reference");

        // Typical use: setting a foreign key without loading the target, e.g.
        //   review.setBook(em.getReference(Book.class, bookId));   // INSERT review only, no SELECT book
        // (Spring Data: repository.getReferenceById(id))

        // Reading state initializes it: SELECT ... FROM book WHERE id=1
        reference.getTitle();
        assertThat(statements()).isEqualTo(1);
        assertThat(Hibernate.isInitialized(reference)).isTrue();
    }

    @Test
    @DisplayName("LazyInitializationException: touching an uninitialized association after the session is gone")
    void lazyInitializationExceptionOnDetachedEntity() {
        // Book is loaded, authors is an uninitialized PersistentSet tied to the current session.
        Book book = em.find(Book.class, 1L);
        assertThat(Hibernate.isInitialized(book.getAuthors())).isFalse();

        // clear() detaches everything, the same thing that happens when a @Transactional
        // service method returns. The collection now has no session to load itself from.
        em.clear();

        // size() needs the content -> "failed to lazily initialize a collection ... - no Session"
        assertThatThrownBy(() -> book.getAuthors().size())
                .isInstanceOf(LazyInitializationException.class);

        // How to fix it properly: load what you need WHILE the session is open:
        //   - fetch join / @EntityGraph in the query (see the tests above)
        //   - map to a DTO inside the @Transactional method (see LibraryService)
        //   - Hibernate.initialize(book.getAuthors()) inside the transaction
        // Workarounds that only hide the problem (and bring N+1 back):
        //   - spring.jpa.open-in-view=true (Spring Boot's default, turned off in this project)
        //   - hibernate.enable_lazy_load_no_trans=true (opens a new session per lazy load)
        //   - switching the association to EAGER
    }
}
