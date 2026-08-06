package com.github.dropguard.summer.data.jdbc.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Framework integration contract: both DI engines assemble over a real Postgres and the {@link
 * PersonRepository} performs a full CRUD round-trip.
 */
@SummerTest
public class PersonJdbcIT extends AbstractFrameworkIT {

    public PersonJdbcIT(BeanContainer context) {
        super(context);
    }

    void jdbcAndQueryTemplateRoundTrip() {
        PersonRepository repo = context.getBean(PersonRepository.class);

        repo.save(new Person(1L, "Ada", 36, "active"));
        repo.save(new Person(2L, "Linus", 54, "active"));

        Person found = repo.findById(1L);
        assertNotNull(found);
        assertEquals("Ada", found.name());

        assertEquals(2, repo.count());

        repo.updateStatus(1L, "inactive");
        assertEquals(1, repo.findByStatus("inactive").size());

        repo.delete(2L);
        assertEquals(1, repo.count());
    }

    void universeContainsFrameworkBeans() {
        assertTrue(context.getBeans(PersonRepository.class).iterator().hasNext());
    }
}
