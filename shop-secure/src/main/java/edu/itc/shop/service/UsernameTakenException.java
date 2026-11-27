package edu.itc.shop.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UsernameTakenException extends RuntimeException {
    public UsernameTakenException(String username) {
        super("Username '" + username + "' is already taken");
    }
}
