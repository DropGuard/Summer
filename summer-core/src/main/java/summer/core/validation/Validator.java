package summer.core.validation;

/**
 * Validates a bean after property binding, before it is used.
 *
 * <p>
 * Implement this interface and register as a {@code @Component} to validate
 * configuration properties or other beans during the Validation Phase of the
 * bean lifecycle.
 * </p>
 *
 * <p>
 * Example:
 * </p>
 *
 * <pre>{@code
 * @Component
 * public class TlsValidator implements Validator<GrpcTlsConfig> {
 *
 *     @Override
 *     public Class<GrpcTlsConfig> targetType() {
 *         return GrpcTlsConfig.class;
 *     }
 *
 *     @Override
 *     public void validate(GrpcTlsConfig config) {
 *         if (config.enabled() && config.certChain() == null) {
 *             throw new ValidationException("TLS enabled but cert-chain is required");
 *         }
 *     }
 * }
 * }</pre>
 *
 * @param <T>
 *            the type to validate
 */
public interface Validator<T> {

	/**
	 * Returns the type this validator applies to.
	 */
	Class<T> targetType();

	/**
	 * Validates the given bean.
	 *
	 * @param bean
	 *            the bean to validate
	 * @throws ValidationException
	 *             if validation fails
	 */
	void validate(T bean);
}
