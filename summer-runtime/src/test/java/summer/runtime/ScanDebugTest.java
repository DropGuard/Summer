package summer.runtime;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.junit.jupiter.api.Test;

class ScanDebugTest {
    @Test
    void testScan() {
        RuntimeApplicationContext.create(); // ensure runtime context is ready
        IndexView index = JandexIndexLoader.buildIndex();
        System.out.println("Classes in index: " + index.getKnownClasses().size());
        for (ClassInfo ci : index.getKnownClasses()) {
            System.out.println("  " + ci.name() + " isInterface=" + ci.isInterface());
        }
    }
}