package com.github.dropguard.summer.engine.spi;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import org.jboss.jandex.IndexView;

/**
 * SPI for {@code @Bean} products whose construction the generic AOT wire generator cannot derive. A
 * module that owns such a product implements this interface (discovered via {@link
 * java.util.ServiceLoader}, declared in {@code META-INF/services}) and returns the construction
 * expression itself — the {@code @Bean} product counterpart of {@code
 * BeanDefinition#aotInstanceExpression} for synthetic beans.
 *
 * <p>Why this exists: some {@code @Bean} products cannot be constructed generically at AOT boot.
 * The canonical case is data-jdbc's {@code EntityMetadataRegistrar}, whose declared constructor
 * takes the discovery {@code IndexView} — deliberately never materialized in the generated
 * container (the AOT path bakes index-derived {@code @RowModel} metadata at codegen time). The
 * owning module computes its own construction here, so the generic generator stays ignorant of
 * individual product classes (previously a hard-coded class-name special case).
 *
 * <p>Contract: the returned expression is the <em>right-hand side</em> of {@code <ProductType> var
 * = <expression>;} emitted verbatim into the generated {@code build()} method. It may reference the
 * variable names of the product's resolved dependencies ({@code
 * InjectionParameter.resolved().get(i).variableName}) and {@code java.util.List.of(...)} literals.
 * Return {@code null} to fall back to the generic construction ({@code config.method(...)} / {@code
 * new Product(...)}).
 */
@Internal
public interface AotProductConstructor {

    /** The product class this provider constructs (fully-qualified name). */
    String productTypeName();

    /**
     * Returns the construction expression for the product bean, or {@code null} for generic
     * construction.
     *
     * @param bean the {@code @Bean} product definition (its resolved parameters are available)
     * @param index the discovery index
     */
    String construction(BeanDefinition bean, IndexView index);
}
