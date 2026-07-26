package com.github.dropguard.summer.web;

/**
 * Router implementation type selection.
 *
 * <p>
 * Used to select which router implementation to use when building HTTP or
 * WebSocket routers. The actual implementations register themselves via
 * {@link RouterRegistry}.
 * </p>
 *
 * @see HttpRouter.Builder#Builder(RouterType)
 * @see WsRouter.Builder#Builder(RouterType)
 */
public enum RouterType {

	/**
	 * High-performance router using Radix Tree (Trie) for path matching.
	 * Recommended for production use.
	 */
	RADIX_TREE,

	/**
	 * Simple router using Map for route storage. Easier to debug, recommended for
	 * development.
	 */
	MAP
}
