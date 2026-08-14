package com.officeonline.exception;

public record ApiError(int status, String message) {
}
