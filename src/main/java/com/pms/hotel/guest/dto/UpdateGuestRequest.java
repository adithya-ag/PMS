package com.pms.hotel.guest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT semantics — full replace. A guest cannot be moved to another hotel. */
public record UpdateGuestRequest(

        @NotBlank
        @Size(max = 120)
        String name,

        @NotBlank
        @Pattern(regexp = "^[6-9]\\d{9}$",
                 message = "Phone number must be a valid 10-digit mobile number")
        String phone,

        @Email(message = "Invalid email format")
        @Size(max = 180)
        String email

) { }
