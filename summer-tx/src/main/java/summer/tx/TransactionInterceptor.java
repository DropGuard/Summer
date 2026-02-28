package summer.tx;

import summer.aop.MethodInterceptor;
import summer.aop.InvocationContext;

/**
 * Transaction interceptor that manages transaction boundaries around method calls.
 */
public class TransactionInterceptor implements MethodInterceptor {
    private final TransactionManager transactionManager;

    public TransactionInterceptor(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Object intercept(InvocationContext context) throws Throwable {
        // Check if method is annotated with @Transactional
        if (context.getMethod().isAnnotationPresent(Transactional.class)) {
            Transactional transactional = context.getMethod().getAnnotation(Transactional.class);
            return handleTransactional(context, transactional.propagation());
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