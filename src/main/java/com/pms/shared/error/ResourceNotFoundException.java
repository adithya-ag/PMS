package com.pms.shared.error;

public class ResourceNotFoundException extends PmsException {
    public ResourceNotFoundException(String resource, Long id) {
        super(String.format("%s not found with ID: %d", resource, id));
    }
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
}   