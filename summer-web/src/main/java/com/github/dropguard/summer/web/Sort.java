package com.github.dropguard.summer.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents sorting parameters for queries. Follows the Spring Data JPA Sort
 * interface design.
 *
 * <p>
 * Usage:
 * </p>
 * 
 * <pre>
 * Sort sort = Sort.by("createdAt").descending();
 * Sort sort = Sort.by("createdAt", "id").descending();
 * Sort sort = Sort.unsorted();
 * </pre>
 */
public record Sort(List<Order> orders) {

	/**
	 * Represents a single sort order.
	 *
	 * @param property
	 *            the property to sort by
	 * @param direction
	 *            the sort direction (ASC or DESC)
	 */
	public record Order(String property, Direction direction) {
	}

	/**
	 * Sort direction enum.
	 */
	public enum Direction {
		ASC, DESC
	}

	/**
	 * Creates a Sort by the given properties in ascending order.
	 *
	 * @param properties
	 *            the properties to sort by
	 * @return a new Sort instance
	 */
	public static Sort by(String... properties) {
		List<Order> orders = new ArrayList<>();
		for (String property : properties) {
			orders.add(new Order(property, Direction.ASC));
		}
		return new Sort(orders);
	}

	/**
	 * Returns an unsorted instance.
	 *
	 * @return an unsorted Sort
	 */
	public static Sort unsorted() {
		return new Sort(Collections.emptyList());
	}

	/**
	 * Returns a new Sort with descending direction for all orders.
	 *
	 * @return a new Sort with descending direction
	 */
	public Sort descending() {
		List<Order> newOrders = new ArrayList<>();
		for (Order order : orders) {
			newOrders.add(new Order(order.property(), Direction.DESC));
		}
		return new Sort(newOrders);
	}

	/**
	 * Returns a new Sort with ascending direction for all orders.
	 *
	 * @return a new Sort with ascending direction
	 */
	public Sort ascending() {
		List<Order> newOrders = new ArrayList<>();
		for (Order order : orders) {
			newOrders.add(new Order(order.property(), Direction.ASC));
		}
		return new Sort(newOrders);
	}

	/**
	 * Returns whether this Sort has any orders.
	 *
	 * @return true if sorted
	 */
	public boolean isSorted() {
		return !orders.isEmpty();
	}

	/**
	 * Parses a sort string in the format "property,direction". Example:
	 * "createdAt,desc" or "createdAt,asc"
	 *
	 * @param sortString
	 *            the sort string to parse
	 * @return a new Sort instance
	 */
	public static Sort parse(String sortString) {
		if (sortString == null || sortString.isBlank()) {
			return unsorted();
		}

		List<Order> orders = new ArrayList<>();
		String[] parts = sortString.split(",");

		for (int i = 0; i < parts.length; i += 2) {
			String property = parts[i].trim();
			Direction direction = Direction.ASC;

			if (i + 1 < parts.length) {
				String dirStr = parts[i + 1].trim().toUpperCase();
				if ("DESC".equals(dirStr)) {
					direction = Direction.DESC;
				}
			}

			orders.add(new Order(property, direction));
		}

		return new Sort(orders);
	}
}
