package com.github.dropguard.summer.data.jdbc.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.data.jdbc.query.QueryTemplate;
import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.Test;

/**
 * Framework integration contract for the fluent {@link QueryTemplate} builder against a real
 * Postgres: both engines exercise the SQL generation path.
 */
@SummerTest
public class PersonQueryIT extends AbstractFrameworkIT {

    public PersonQueryIT(BeanContainer context) {
        super(context);
    }

    @Test
    void selectByCriteriaAndCount() {
        PersonRepository repo = context.getBean(PersonRepository.class);
        repo.save(new Person(10L, "Grace", 40, "active"));
        repo.save(new Person(11L, "Margaret", 44, "active"));
        repo.save(new Person(12L, "Katherine", 38, "retired"));

        // select by criteria
        assertEquals(2, repo.findByStatus("active").size());

        // partial update changes only the targeted column
        repo.updateStatus(10L, "retired");
        assertEquals(2, repo.findByStatus("retired").size());
        assertEquals("Grace", repo.findById(10L).name());

        // count
        assertEquals(3, repo.count());
    }
}
