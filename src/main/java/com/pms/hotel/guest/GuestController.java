package com.pms.hotel.guest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pms.hotel.guest.dto.CreateGuestRequest;
import com.pms.hotel.guest.dto.GuestResponse;
import com.pms.hotel.guest.dto.UpdateGuestRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * /api/hotels/{hotelId}/guests — a guest belongs to exactly one hotel, so the URL says so.
 */
@RestController
@RequestMapping("/api/hotels/{hotelId}/guests")
@RequiredArgsConstructor
public class GuestController {

    private final GuestService service;

    @PostMapping
    public ResponseEntity<GuestResponse> createGuest(
            @PathVariable Long hotelId,
            @Valid @RequestBody CreateGuestRequest request) {

        GuestResponse response = service.createGuest(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{guestId}")
    public ResponseEntity<GuestResponse> getGuest(
            @PathVariable Long hotelId,
            @PathVariable Long guestId) {

        return ResponseEntity.ok(service.getGuest(hotelId, guestId));
    }

    /**
     * List guests, optionally filtered by phone.
     *
     *   GET /api/hotels/1/guests                       -> everyone, paged
     *   GET /api/hotels/1/guests?phone=9876543210      -> the returning-guest lookup
     *
     * A FILTER is a query parameter, not a path segment: /guests?phone=X is still the
     * guests collection, narrowed. A path segment would imply a different resource.
     */
    @GetMapping
    public ResponseEntity<Page<GuestResponse>> listGuests(
            @PathVariable Long hotelId,
            @RequestParam(required = false) String phone,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(service.listGuests(hotelId, phone, pageable));
    }

    @PutMapping("/{guestId}")
    public ResponseEntity<GuestResponse> updateGuest(
            @PathVariable Long hotelId,
            @PathVariable Long guestId,
            @Valid @RequestBody UpdateGuestRequest request) {

        return ResponseEntity.ok(service.updateGuest(hotelId, guestId, request));
    }

    @DeleteMapping("/{guestId}")
    public ResponseEntity<Void> deleteGuest(
            @PathVariable Long hotelId,
            @PathVariable Long guestId) {

        service.deleteGuest(hotelId, guestId);
        return ResponseEntity.noContent().build();
    }
}
