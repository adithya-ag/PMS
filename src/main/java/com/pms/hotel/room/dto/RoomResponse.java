package com.pms.hotel.room.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.pms.hotel.room.RoomStatus;
import com.pms.hotel.room.RoomType;

/**
 * Outbound. Audit fields are fine here — they are read-only to the client.
 *
 * hotelId is a flat Long, NOT a nested HotelResponse. Serialising the whole Hotel object
 * would drag the relationship into the JSON and, with lazy loading, fire an extra query
 * per room. Flatten the foreign key instead.
 */
public record RoomResponse(
        Long id,
        Long hotelId,
        String roomNumber,
        Integer floor,
        RoomType type,
        RoomStatus status,
        BigDecimal rate,
        Instant createdAt,
        Long createdBy,
        Instant updatedAt,
        Long updatedBy
) { }
