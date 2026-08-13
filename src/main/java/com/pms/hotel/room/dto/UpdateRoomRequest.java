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
 * PUT semantics — full replace, so every field is required (same choice as UpdateHotelRequest).
 * hotelId is absent on purpose: a room cannot be moved to a different hotel.
 */
public record UpdateRoomRequest(

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

        @NotNull
        @DecimalMin(value = "0.0", message = "Rate cannot be negative")
        @Digits(integer = 8, fraction = 2)
        BigDecimal rate

) { }
