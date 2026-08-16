package com.github.dropguard.summer.engine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.engine.testfixtures.pipeline.ConditionalController;
import com.github.dropguard.summer.engine.testfixtures.pipeline.NotABean;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

/**
 * Pipeline-level behavior (the application-level contracts — route parity, dedup, fail-fast — are
 * covered behaviorally by the TCK; this narrow test covers the one property with no app-level
 * observable at this layer: a conditioned-out bean must not survive into the resolved set).
 */
class BuildPipelineTest {

    @Test
    void conditionedOutBeanIsDropped() {
        BeanDeployment deployment =
                BeanDeployment.forNarrow(index(ConditionalController.class, NotABean.class), null);

        BuildPipeline.Resolved resolved = BuildPipeline.resolve(deployment, List.of());

        assertTrue(
                resolved.sorted().stream()
                        .noneMatch(
                                b -> b.qualifiedName.equals(ConditionalController.class.getName())),
                "a bean whose @ConditionalOnBean target is not a bean must be dropped");
    }

    private static IndexView index(Class<?>... classes) {
        Indexer indexer = new Indexer();
        for (Class<?> c : classes) {
            String resource = "/" + c.getName().replace('.', '/') + ".class";
            try (InputStream is = c.getResourceAsStream(resource)) {
                assertNotNull(is, "cannot read class bytes for indexing: " + resource);
                indexer.index(is);
            } catch (IOException e) {
                throw new AssertionError("failed to index " + c.getName(), e);
            }
        }
        return indexer.complete();
    }
}
