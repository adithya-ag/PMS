package com.pms.hotel.profile.dto;

import java.math.BigDecimal;


public record RoomSummary(
    Long id,
    String roomNumber,
    int floor,
    String type,
    String status,
    BigDecimal rate
) {}
