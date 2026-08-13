package com.pms.hotel.guest;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, Long> {

    /** Tenant-scoped lookup — never findById alone, or you can read another hotel's guest. */
    Optional<Guest> findByIdAndHotelId(Long id, Long hotelId);

    Page<Guest> findByHotelId(Long hotelId, Pageable pageable);

    /**
     * The returning-guest lookup: "has this phone number stayed here before?"
     *
     * This is what idx_guests_phone (hotel_id, phone) was designed for — the index
     * columns are in the same order as the query's predicates, so Postgres can seek
     * straight to the matching rows instead of scanning the table.
     *
     * Not unique: the same phone may appear more than once in one hotel (bad data,
     * a family sharing a number), so this returns a Page, not an Optional.
     */
    Page<Guest> findByHotelIdAndPhone(Long hotelId, String phone, Pageable pageable);
}
