package sonyflake.exception;

public class SonyflakeException extends RuntimeException {

    public SonyflakeException() {
    }

    public SonyflakeException(String message) {
        super(message);
    }

    public SonyflakeException(String message, Throwable cause) {
        super(message, cause);
    }

    public SonyflakeException(Throwable cause) {
        super(cause);
    }

    public SonyflakeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
