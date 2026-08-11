package com.pms.shared.error;

import lombok.Getter;

@Getter
public class PmsException extends RuntimeException{

    public PmsException(String message) {
        super(message);
    }
    
    public PmsException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
