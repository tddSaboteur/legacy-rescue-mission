package org.apache.camel.component.file.remote.exception;

public class SftpClientException extends RuntimeException{
    private final Integer statusCode;

    public SftpClientException(String message, Throwable cause) {
        this(message, cause, null);
    }
    public SftpClientException(String message, Throwable cause, Integer statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
