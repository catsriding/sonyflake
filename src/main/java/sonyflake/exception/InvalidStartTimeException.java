package sonyflake.exception;

public class InvalidStartTimeException extends SonyflakeException {

    public InvalidStartTimeException(String message) {
        super(message);
    }

    public InvalidStartTimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
