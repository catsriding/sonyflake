package sonyflake.exception;

public class InvalidMachineIdException extends SonyflakeException {

    public InvalidMachineIdException(String message) {
        super(message);
    }

    public InvalidMachineIdException(String message, Throwable cause) {
        super(message, cause);
    }
}
