package com.pms.hotel.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pms.hotel.booking.dto.BookingResponse;
import com.pms.hotel.booking.dto.CreateBookingRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hotels/{hotelId}/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService service;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
        @PathVariable Long hotelId, 
        @RequestBody @Valid CreateBookingRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBooking(hotelId, request));
    }

    @GetMapping
    public ResponseEntity<Page<BookingResponse>> listBooking(
        @PathVariable Long hotelId,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable page){
        return ResponseEntity.ok(service.listBooking(hotelId, page));
    }

    // This GetMapping needs it because hotelId is already taken care of in the RequestMapping
    // So we specfically mention the bookingId here in the url.
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(
        @PathVariable Long hotelId, 
        @PathVariable Long bookingId
        //  We need to use the PathVariable twice if there are two things we are trying to extract from the url
        //  else spring will treat it as unAnnotated 
    ){
        return ResponseEntity.ok(service.getBooking(hotelId, bookingId));
    }

    // Three named action endpoints, ONE service method behind them.
    // Named URLs (/cancel) over a generic PATCH {"status": "..."} because the URL then says what
    // the action MEANS — and later, each can carry its own @PreAuthorize role.
    // PATCH not PUT: these change one field, they don't replace the resource.

    @PatchMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
        @PathVariable Long hotelId,
        @PathVariable Long bookingId
    ){
        return ResponseEntity.ok(service.changeStatus(hotelId, bookingId, BookingStatus.CANCELLED));
    }

    @PatchMapping("/{bookingId}/check-in")
    public ResponseEntity<BookingResponse> checkIn(
        @PathVariable Long hotelId,
        @PathVariable Long bookingId
    ){
        return ResponseEntity.ok(service.changeStatus(hotelId, bookingId, BookingStatus.CHECKED_IN));
    }

    @PatchMapping("/{bookingId}/check-out")
    public ResponseEntity<BookingResponse> checkOut(
        @PathVariable Long hotelId,
        @PathVariable Long bookingId
    ){
        return ResponseEntity.ok(service.changeStatus(hotelId, bookingId, BookingStatus.CHECKED_OUT));
    }
}
