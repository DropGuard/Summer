package summer.tck.fixtures.di.errors;

import summer.core.Component;

/**
 * Participates in a deliberate dependency cycle (A -> B -> A) to exercise the
 * container's circular-dependency detection.
 */
@Component
public class CycleNodeA {

	private final CycleNodeB nodeB;

	public CycleNodeA(CycleNodeB nodeB) {
		this.nodeB = nodeB;
	}

	public CycleNodeB nodeB() {
		return nodeB;
	}
}
