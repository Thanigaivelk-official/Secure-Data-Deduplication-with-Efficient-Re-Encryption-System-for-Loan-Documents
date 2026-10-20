package com.example.loandocs.exception;

public class SecurityCryptoException extends AppException {
    public SecurityCryptoException(String message) {
        super(message);
    }

    public SecurityCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
