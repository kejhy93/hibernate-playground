package org.hejnaluk.hibernatetest.hibernatetest.lessons;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * Base class for the lessons.
 * <p>
 * Every test runs against the real Postgres (./pg.sh start) inside a transaction
 * that is rolled back afterwards, so the seed data is never changed. Play freely.
 * <p>
 * Watch the console: each SQL statement Hibernate runs is logged with its bind parameters.
 */
@DataJpaTest(showSql = false) // SQL logging is configured in application.properties
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // use Postgres, not an embedded DB
abstract class HibernateLesson {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** Bound to the test's transaction: this is "the" persistence context of the test. */
    @Autowired
    protected EntityManager em;

    @Autowired
    private EntityManagerFactory emf;

    protected Statistics stats;

    @BeforeEach
    void setUpLesson(TestInfo testInfo) {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        log.info("===== {} =====", testInfo.getDisplayName());
    }

    /** Number of JDBC statements Hibernate prepared since the last reset. */
    protected long statements() {
        return stats.getPrepareStatementCount();
    }

    /** Write pending changes, empty the persistence context and reset the counters. */
    protected void flushAndClear() {
        em.flush();
        em.clear();
        stats.clear();
    }

    protected void step(String description) {
        log.info("----- {}", description);
    }
}
