package summer.tx;

import summer.aop.Interceptor;
import summer.aop.InterceptorChain;
import summer.aop.MethodInterceptor;
import summer.core.annotation.ConditionalOnBean;

/**
 * Transaction interceptor that manages transaction boundaries around method
 * calls.
 *
 * <p>
 * Bound to {@code @Transactional} via {@code @InterceptorBinding}.
 * </p>
 *
 * <p>
 * This is a framework infrastructure bean provided by
 * {@link TxInfrastructureConfiguration}.
 * </p>
 */
@Interceptor
@ConditionalOnBean(TransactionManager.class)
@Transactional
public class TransactionInterceptor implements MethodInterceptor {
	private static final ThreadLocal<Boolean> interceptorActive = ThreadLocal.withInitial(() -> false);
	private final TransactionManager transactionManager;

	public TransactionInterceptor(TransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	public static boolean isInterceptorActive() {
		return interceptorActive.get();
	}

	@Override
	public Object intercept(InterceptorChain chain) throws Throwable {
		// Check if method is annotated with @Transactional
		if (chain.getMethod().isAnnotationPresent(Transactional.class)) {
			boolean alreadyActive = interceptorActive.get();
			try {
				interceptorActive.set(true);
				return handleTransactional(chain);
			} finally {
				if (!alreadyActive) {
					interceptorActive.remove();
				}
			}
		}

		return chain.proceed();
	}

	private Object handleTransactional(InterceptorChain chain) throws Throwable {
		TransactionStatus transaction = null;

		try {
			transaction = transactionManager.begin();

			Object result = chain.proceed();

			if (transaction != null && !transaction.isRollbackOnly()) {
				transactionManager.commit(transaction);
			} else if (transaction != null) {
				transactionManager.rollback(transaction);
			}

			return result;
		} catch (Exception e) {
			if (transaction != null) {
				transactionManager.rollback(transaction);
			}
			throw e;
		}
	}
}
