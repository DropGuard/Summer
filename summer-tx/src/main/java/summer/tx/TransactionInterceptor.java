package summer.tx;

import summer.aop.InvocationContext;
import summer.aop.MethodInterceptor;
import summer.core.Component;
import summer.core.annotation.ConditionalOnBean;

/**
 * Transaction interceptor that manages transaction boundaries around method
 * calls.
 *
 * <p>
 * Bound to {@code @Transactional} via {@code @InterceptorBinding}.
 * </p>
 */
@Component
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
	public Object intercept(InvocationContext context) throws Throwable {
		// Check if method is annotated with @Transactional
		if (context.getMethod().isAnnotationPresent(Transactional.class)) {
			boolean alreadyActive = interceptorActive.get();
			try {
				interceptorActive.set(true);
				Transactional transactional = context.getMethod().getAnnotation(Transactional.class);
				return handleTransactional(context, transactional.propagation());
			} finally {
				if (!alreadyActive) {
					interceptorActive.remove();
				}
			}
		}

		return context.proceed();
	}

	private Object handleTransactional(InvocationContext context, TransactionPropagation propagation) throws Throwable {
		TransactionStatus transaction = null;

		try {
			transaction = transactionManager.begin();

			Object result = context.proceed();

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