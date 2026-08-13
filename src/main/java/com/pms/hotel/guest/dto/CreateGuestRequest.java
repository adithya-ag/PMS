package com.pms.hotel.guest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * hotelId is absent on purpose — it comes from the URL path, never the body.
 *
 * Note email: @Email but NOT @NotBlank. The column is nullable, so a walk-in guest
 * with no email address is valid. @Email permits null; it only rejects a value that
 * is present and malformed.
 */
public record CreateGuestRequest(

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
