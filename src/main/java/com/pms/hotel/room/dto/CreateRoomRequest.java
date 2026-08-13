package com.pms.hotel.room.dto;

import java.math.BigDecimal;

import com.pms.hotel.room.RoomStatus;
import com.pms.hotel.room.RoomType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Inbound. Note what is NOT here:
 *   - id        : the database generates it
 *   - hotelId   : comes from the URL path, not the body. A client must never be able
 *                 to create a room in someone else's hotel by editing the JSON.
 *   - audit fields : set by Spring Data auditing
 */
public record CreateRoomRequest(

        @NotBlank
        @Size(max = 10)
        String roomNumber,

        @NotNull
        @Min(value = 0, message = "Floor cannot be negative")
        Integer floor,

        @NotNull
        RoomType type,

        @NotNull
        RoomStatus status,

        // numeric(10,2) in the schema. BigDecimal, never double — binary floating point
        // cannot represent money exactly.
        @NotNull
        @DecimalMin(value = "0.0", message = "Rate cannot be negative")
        @Digits(integer = 8, fraction = 2)
        BigDecimal rate

) { }
