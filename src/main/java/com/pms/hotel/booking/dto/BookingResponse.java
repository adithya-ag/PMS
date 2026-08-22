package com.pms.hotel.booking.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.pms.hotel.booking.BookingStatus;
import com.pms.hotel.room.RoomType;

public record BookingResponse(
    Long id,
    Long hotelId,
    Long guestId,
    Long roomId,
    RoomType roomType,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    BookingStatus status,
    Instant createdAt,
    Long createdBy,
    Instant updatedAt,
    Long updatedBy
) {}
