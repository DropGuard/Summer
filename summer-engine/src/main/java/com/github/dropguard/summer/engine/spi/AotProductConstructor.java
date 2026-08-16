package com.github.dropguard.summer.engine.spi;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import java.util.List;
import org.jboss.jandex.IndexView;

/**
 * SPI for {@code @Bean} products whose AOT assembly the generic wire generator cannot derive. A
 * module that owns such a product implements this interface (discovered via {@link
 * java.util.ServiceLoader}, declared in {@code META-INF/services}) and contributes either or both
 * of the two assembly forms:
 *
 * <ul>
 *   <li>{@link #construction(BeanDefinition, IndexView)} — a construction <em>override</em>: the
 *       product's declared construction cannot be derived generically. The canonical case is
 *       data-jdbc's {@code EntityMetadataRegistrar}, whose declared constructor takes the discovery
 *       {@code IndexView} — deliberately never materialized in the generated container (the AOT
 *       path bakes index-derived {@code @RowModel} metadata at codegen time). The owning module
 *       computes its own construction here, so the generic generator stays ignorant of individual
 *       product classes (previously a hard-coded class-name special case).
 *   <li>{@link #postConstruction(BeanDefinition, IndexView)} — assembly-time writes emitted after
 *       the construction (override or generic): the AOT counterpart of a runtime assembly-time
 *       filler bean. The canonical case is data-jdbc's {@code JdbcTemplate}: on the runtime engine
 *       a registrar bean fills the row mappers during assembly; here the owning module emits the
 *       same {@code registerMapper} calls as generated statements, so both engines fill identically
 *       and the container seal phase freezes the result.
 * </ul>
 *
 * <p>Contract: the {@code construction} result is the <em>right-hand side</em> of {@code
 * <ProductType> var = <expression>;} emitted verbatim into the generated {@code build()} method. It
 * may reference the variable names of the product's resolved dependencies ({@code
 * InjectionParameter.resolved().get(i).variableName}) and {@code java.util.List.of(...)} literals.
 * Return {@code null} to fall back to the generic construction ({@code config.method(...)} / {@code
 * new Product(...)}). Each {@code postConstruction} string is the <em>right-hand side</em> of a
 * {@code var.<string>;} statement (the product variable is the receiver) — typically a registration
 * call whose arguments may reference dependency variable names. Statements are emitted after
 * construction and before registration.
 */
@Internal
public interface AotProductConstructor {

    /** The product class this provider assembles (fully-qualified name). */
    String productTypeName();

    /**
     * Returns the construction expression for the product bean, or {@code null} for generic
     * construction.
     *
     * @param bean the {@code @Bean} product definition (its resolved parameters are available)
     * @param index the discovery index
     */
    default String construction(BeanDefinition bean, IndexView index) {
        return null;
    }

    /**
     * Returns post-construction initialization statements for the product bean, each the right-hand
     * side of a {@code <productVar>.<string>;} statement emitted after construction and before
     * registration. Empty when the product needs no assembly-time writes beyond construction.
     *
     * @param bean the {@code @Bean} product definition (its resolved parameters are available)
     * @param index the discovery index
     */
    default List<String> postConstruction(BeanDefinition bean, IndexView index) {
        return List.of();
    }
}
