package com.techietable.collage.model;

public record ContactRequest(String name, String email, String message, boolean wantHandle, String handle) {}
