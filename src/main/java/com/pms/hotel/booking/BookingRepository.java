package com.pms.hotel.booking;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pms.hotel.room.Room;
import com.pms.hotel.room.RoomType;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * The statuses that make a room UNAVAILABLE. Defined once, here.
     * CANCELLED and CHECKED_OUT block nothing. PENDING is excluded by choice —
     * an abandoned booking must not hold a room forever.
     *
     * ⚠️ Must stay in sync with the EXCLUDE constraint we add to schema.sql later.
     *
     * (Fields in an interface are implicitly public static final.)
     */
    List<BookingStatus> BLOCKING_STATUSES = List.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

    // ── Tenant-scoped basics ────────────────────────────────────────────────
    Optional<Booking> findByIdAndHotelId(Long id, Long hotelId);

    Page<Booking> findByHotelId(Long hotelId, Pageable pageable);

    // ── The overlap rule ────────────────────────────────────────────────────
    //
    // A stay is the HALF-OPEN interval [check_in, check_out).
    // Two stays overlap when:   a.in < b.out   AND   a.out > b.in
    //
    // Strict < and > on purpose: a guest checking OUT on the 9th and another
    // checking IN on the 9th do not conflict — the room is free that night.
    //
    // NOTE: the statuses are passed as a BOUND PARAMETER, never written inline
    // in the JPQL. Inline enum literals make Hibernate try to cast to a Postgres
    // type named after the Java class ("bookingstatus"), which does not exist —
    // our type is "booking_status". Binding uses the column's real type instead.

    @Query("""
           select count(b) > 0 from Booking b
           where b.room.id = :roomId
             and b.status in :statuses
             and b.checkInDate  < :checkOut
             and b.checkOutDate > :checkIn
           """)
    boolean existsOverlapping(@Param("roomId")   Long roomId,
                              @Param("checkIn")  LocalDate checkIn,
                              @Param("checkOut") LocalDate checkOut,
                              @Param("statuses") Collection<BookingStatus> statuses);

    /** Convenience overload so callers don't repeat the blocking-status list. */
    default boolean existsOverlapping(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        return existsOverlapping(roomId, checkIn, checkOut, BLOCKING_STATUSES);
    }

    /**
     * Rooms of this type in this hotel with NO blocking booking overlapping the dates.
     * Empty list means everything is taken.
     */
    @Query("""
           select r from Room r
           where r.hotel.id = :hotelId
             and r.type     = :roomType
             and not exists (
                   select 1 from Booking b
                   where b.room = r
                     and b.status in :statuses
                     and b.checkInDate  < :checkOut
                     and b.checkOutDate > :checkIn
                 )
           order by r.roomNumber
           """)
    List<Room> findAvailableRooms(@Param("hotelId")  Long hotelId,
                                  @Param("roomType") RoomType roomType,
                                  @Param("checkIn")  LocalDate checkIn,
                                  @Param("checkOut") LocalDate checkOut,
                                  @Param("statuses") Collection<BookingStatus> statuses);

    /** Convenience overload. */
    default List<Room> findAvailableRooms(Long hotelId, RoomType roomType,
                                          LocalDate checkIn, LocalDate checkOut) {
        return findAvailableRooms(hotelId, roomType, checkIn, checkOut, BLOCKING_STATUSES);
    }
}
