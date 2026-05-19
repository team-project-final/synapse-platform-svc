package io.synapse.platform.shared.exception;

public abstract class BusinessException extends RuntimeException {

    private final String errorCode;
    private final int status;

    protected BusinessException(String errorCode, int status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatus() {
        return status;
    }
}
