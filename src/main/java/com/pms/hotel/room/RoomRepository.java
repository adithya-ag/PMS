package com.pms.hotel.room;

import java.util.Optional;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.*;

@Repository
public interface RoomRepository extends JpaRepository<Room , Long>{
 
    Optional<Room> findByIdAndHotelId(Long id, Long hotelId);
    Page<Room>     findByHotelId(Long hotelId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Room> findForUpdateByIdAndHotelId(Long id, Long hotelId);
}
