package com.github.dropguard.summer.core.bean;

import com.github.dropguard.summer.core.Internal;
import java.util.Objects;

/**
 * A single Mockito mock bound to the bean type it replaces.
 *
 * <p>
 * This is the framework's internal representation of a {@code @Mock}
 * constructor parameter: {@code targetType} is the declared parameter type (the
 * bean to replace at discovery stage) and {@code instance} is the Mockito mock
 * created for it. Binding the two into one struct guarantees they can never
 * desync — the mock is always installed for exactly the type it was declared to
 * replace.
 * </p>
 *
 * <p>
 * The discovery stage reads {@link #targetType()} to remove the real bean
 * definition (so it is never instantiated, and neither is its dependency
 * closure); the instance-build stage reads {@link #instance()} to register the
 * mock under that type. Both Runtime and AOT engines consume the same
 * {@code MockedBean}, so replacement behaviour is identical across engines.
 * </p>
 *
 * <p>
 * This replaces the former {@code externalBeans}/{@code mocks}
 * {@code Object...} channel: there is no user-facing hand-rolled instance
 * collection — mocks are declared with {@code @Mock} and assembled by the
 * framework, Quarkus-style. Lives in {@code summer-core} (a pure data carrier,
 * no Mockito dependency) so both the runtime and AOT engines can reference it
 * without a dependency cycle.
 * </p>
 */
@Internal
public final class MockedBean {

	private final Class<?> targetType;
	private final Object instance;

	private MockedBean(Class<?> targetType, Object instance) {
		this.targetType = Objects.requireNonNull(targetType, "targetType");
		this.instance = Objects.requireNonNull(instance, "instance");
	}

	/**
	 * Creates a mocked bean for the given target type and its Mockito instance.
	 *
	 * @param targetType
	 *            the declared {@code @Mock} parameter type — the bean type to
	 *            replace
	 * @param instance
	 *            the Mockito mock instance
	 * @return the bound mocked bean
	 */
	public static MockedBean of(Class<?> targetType, Object instance) {
		return new MockedBean(targetType, instance);
	}

	/**
	 * The bean type this mock replaces (authoritative — not
	 * {@code instance.getClass()}).
	 */
	public Class<?> targetType() {
		return targetType;
	}

	/** The Mockito mock instance to register. */
	public Object instance() {
		return instance;
	}

	/**
	 * The fully-qualified name of the replaced type, for discovery-stage matching.
	 */
	public String targetTypeName() {
		return targetType.getName();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof MockedBean other))
			return false;
		return targetType.equals(other.targetType) && instance.equals(other.instance);
	}

	@Override
	public int hashCode() {
		return Objects.hash(targetType, instance);
	}

	@Override
	public String toString() {
		return "MockedBean{" + targetType.getName() + "}";
	}
}
