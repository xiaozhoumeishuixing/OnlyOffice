package com.officeonline.exception;

public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String extension) {
        super("Unsupported file type: " + extension);
    }
}
