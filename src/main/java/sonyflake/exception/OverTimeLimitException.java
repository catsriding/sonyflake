package sonyflake.exception;

public class OverTimeLimitException extends SonyflakeException {

    public OverTimeLimitException(String message) {
        super(message);
    }

    public OverTimeLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
