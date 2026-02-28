package summer.core;

/**
 * Base exception class for all Summer framework exceptions.
 */
public class SummerException extends RuntimeException {
    public SummerException(String message) {
        super(message);
    }

    public SummerException(String message, Throwable cause) {
        super(message, cause);
    }
}