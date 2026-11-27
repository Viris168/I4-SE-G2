package edu.itc.shop.service;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String username) {
        super("User '" + username + "' not found");
    }
}
