package com.github.dropguard.summer.core.validation;

/**
 * Validates a bean after property binding, before it is used.
 *
 * <p>Implement this interface and register as a {@code @Component} to validate configuration
 * properties or other beans during the Validation Phase of the bean lifecycle.
 *
 * <p>Example:
 *
 * <pre>
 * {
 * 	&#64;code
 * 	&#64;Component
 * 	public class TlsConfigValidator implements Validator&lt;MyTlsConfig&gt; {
 *
 * 		&#64;Override
 * 		public Class&lt;MyTlsConfig&gt; targetType() {
 * 			return MyTlsConfig.class;
 * 		}
 *
 * 		@Override
 * 		public void validate(MyTlsConfig config) {
 * 			if (config.enabled() &amp;&amp; config.certChain() == null) {
 * 				throw new ConfigValidationException("TLS enabled but cert-chain is required");
 * 			}
 * 		}
 * 	}
 * }
 * </pre>
 *
 * @param <T> the type to validate
 */
public interface Validator<T> {

    /** Returns the type this validator applies to. */
    Class<T> targetType();

    /**
     * Validates the given bean.
     *
     * @param bean the bean to validate
     * @throws ConfigValidationException if validation fails
     */
    void validate(T bean);
}
