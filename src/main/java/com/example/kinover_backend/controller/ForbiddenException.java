// src/main/java/com/example/kinover_backend/controller/ForbiddenException.java
package com.example.kinover_backend.controller;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
