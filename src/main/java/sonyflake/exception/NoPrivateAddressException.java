package sonyflake.exception;

public class NoPrivateAddressException extends SonyflakeException {

    public NoPrivateAddressException(String message) {
        super(message);
    }

    public NoPrivateAddressException(String message, Throwable cause) {
        super(message, cause);
    }
}
