package com.pms.shared.error;

public class BookingConflictException extends PmsException {
    public BookingConflictException(){
        super("Rooms Not available for the selected dates");
    }
    
    public BookingConflictException(String message){
        super(message);
    }
}
