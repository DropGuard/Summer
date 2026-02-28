package summer.web;

import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaVersionTest {

    @Test
    public void testJavaVersionIsAtLeast25() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        String version = runtimeMXBean.getSpecVersion();
        int majorVersion = Integer.parseInt(version.split("\\.")[0]);
        
        assertTrue(majorVersion >= 25, 
            "This project requires Java 25 or later, but you're running Java " + majorVersion);
    }
    
    @Test
    public void testJavaVendorAndVersion() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        System.out.println("Java Vendor: " + runtimeMXBean.getVmVendor());
        System.out.println("Java Version: " + runtimeMXBean.getVmVersion());
        System.out.println("Java Home: " + System.getProperty("java.home"));
        System.out.println("Java Spec Version: " + runtimeMXBean.getSpecVersion());
        
        // 简单的验证，确保这些信息不为 null
        assertTrue(runtimeMXBean.getVmVendor() != null && !runtimeMXBean.getVmVendor().isEmpty());
        assertTrue(runtimeMXBean.getVmVersion() != null && !runtimeMXBean.getVmVersion().isEmpty());
        assertTrue(System.getProperty("java.home") != null && !System.getProperty("java.home").isEmpty());
    }
}
