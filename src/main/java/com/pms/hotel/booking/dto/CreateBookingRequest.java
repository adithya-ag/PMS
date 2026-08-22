package com.pms.hotel.booking.dto;

import java.time.LocalDate;

import com.pms.hotel.room.RoomType;

import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
    @NotNull
    Long guestId,

    Long roomId,

    @NotNull
    RoomType roomType,

    @NotNull
    LocalDate checkInDate,
    
    @NotNull
    LocalDate checkOutDate
) {} 