// src/main/java/com/example/kinover_backend/controller/BadRequestException.java
package com.example.kinover_backend.controller;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
