package com.pms.shared.error;

public class InvalidDateRangeException extends PmsException {
    public InvalidDateRangeException() {
        super("Check-out date must be after check-in date");
    }
}