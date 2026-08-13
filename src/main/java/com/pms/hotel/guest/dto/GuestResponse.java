package com.pms.hotel.guest.dto;

import java.time.Instant;

/** hotelId flattened to a Long — never nest the Hotel object (see RoomResponse). */
public record GuestResponse(
        Long id,
        Long hotelId,
        String name,
        String phone,
        String email,
        Instant createdAt,
        Long createdBy,
        Instant updatedAt,
        Long updatedBy
) { }
