// src/main/java/com/example/kinover_backend/controller/NotFoundException.java
package com.example.kinover_backend.controller;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
