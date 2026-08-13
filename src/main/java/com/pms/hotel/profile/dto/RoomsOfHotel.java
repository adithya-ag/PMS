package com.pms.hotel.profile.dto;

import java.time.Instant;
import java.util.List;


public record RoomsOfHotel(
    Long id, 
    String name, 
    List<RoomSummary> rooms,
    String email, 
    String phone, 
    String city, 
    String state, 
    String pincode, 
    Instant createdAt, 
    Long createdBy, 
    Instant updatedAt, 
    Long updatedBy) {} 