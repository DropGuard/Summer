package summer.tx;

/**
 * Transaction status interface that represents the state of a transaction.
 */
public interface TransactionStatus {
    boolean isActive();
    boolean isRollbackOnly();
    void setRollbackOnly();
    void flush();
}