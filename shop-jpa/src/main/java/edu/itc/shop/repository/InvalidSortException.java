package edu.itc.shop.repository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Challenge: not an IllegalArgumentException, which the repository proxy would translate
// into InvalidDataAccessApiUsageException
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String message) {
        super(message);
    }
}
