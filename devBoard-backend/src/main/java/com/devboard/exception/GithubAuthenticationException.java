package com.devboard.exception;

public class GithubAuthenticationException extends UnauthorizedException {
    public GithubAuthenticationException(String message) { super(message); }
}
