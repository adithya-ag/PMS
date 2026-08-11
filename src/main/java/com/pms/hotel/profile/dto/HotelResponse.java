package com.pms.hotel.profile.dto;

import java.time.Instant;

public record HotelResponse(Long id, String name, String email, String phone, String city, String state, String pincode, Instant createdAt, Long createdBy, Instant updatedAt, Long updatedBy) {} 
