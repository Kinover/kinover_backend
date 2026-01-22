// src/main/java/com/example/kinover_backend/controller/UnauthorizedException.java
package com.example.kinover_backend.controller;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
