package com.pms.hotel.profile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateHotelRequest(
    @NotBlank String name,
    @NotBlank @Email String email,
    @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Phone number must be a valid 10-digit mobile number") String phone,
    @NotBlank  String city,
    @NotBlank String state,
    @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must contain only numbers") String pincode
) {}
