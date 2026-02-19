package com.tus.microservices.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when there are not enough copies available.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientInventoryException extends RuntimeException {

    public InsufficientInventoryException(String message) {
        super(message);
    }

    public InsufficientInventoryException(String isbn, String branchId) {
        super(String.format("No copies available for ISBN '%s' at branch '%s'", isbn, branchId));
    }
}
