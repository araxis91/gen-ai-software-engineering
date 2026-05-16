package com.homework2.support.importer;

public class MalformedImportFileException extends RuntimeException {
    public MalformedImportFileException(String message) {
        super(message);
    }

    public MalformedImportFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
