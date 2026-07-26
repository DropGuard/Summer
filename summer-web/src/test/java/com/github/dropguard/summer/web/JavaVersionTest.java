package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import org.junit.jupiter.api.Test;

public class JavaVersionTest {

	@Test
	public void testJavaVersionIsAtLeast26() {
		RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
		String version = runtimeMXBean.getSpecVersion();
		int majorVersion = Integer.parseInt(version.split("\\.")[0]);

		assertTrue(majorVersion >= 26,
				"This project requires Java 26 or later, but you're running Java " + majorVersion);
	}

	@Test
	public void testJavaVendorAndVersion() {
		RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
		System.out.println("Java Vendor: " + runtimeMXBean.getVmVendor());
		System.out.println("Java Version: " + runtimeMXBean.getVmVersion());
		System.out.println("Java Home: " + System.getProperty("java.home"));
		System.out.println("Java Spec Version: " + runtimeMXBean.getSpecVersion());

		assertTrue(runtimeMXBean.getVmVendor() != null && !runtimeMXBean.getVmVendor().isEmpty());
		assertTrue(runtimeMXBean.getVmVersion() != null && !runtimeMXBean.getVmVersion().isEmpty());
		assertTrue(System.getProperty("java.home") != null && !System.getProperty("java.home").isEmpty());
	}
}
