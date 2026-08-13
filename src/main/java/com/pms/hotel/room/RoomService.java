package com.pms.hotel.room;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pms.hotel.profile.Hotel;
import com.pms.hotel.profile.HotelRepository;
import com.pms.hotel.room.dto.CreateRoomRequest;
import com.pms.hotel.room.dto.RoomResponse;
import com.pms.hotel.room.dto.UpdateRoomRequest;
import com.pms.hotel.room.dto.mapper.RoomMapper;
import com.pms.shared.error.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Same shape as HotelService, with one difference that matters:
 *
 *   EVERY method takes hotelId and scopes its query by it.
 *
 * This is multi-tenancy enforced in code. `findById(roomId)` alone would happily return
 * a room belonging to a different hotel — a cross-tenant data leak. `findByIdAndHotelId`
 * makes that impossible.
 *
 * For now hotelId arrives from the URL path. In phase 8 it will come from the JWT instead,
 * and these signatures stay exactly the same — only the source of the value changes.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepo;
    private final HotelRepository hotelRepo;
    private final RoomMapper mapper;

    @Transactional
    public RoomResponse createRoom(Long hotelId, CreateRoomRequest request) {
        Hotel hotel = hotelRepo.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", hotelId));

        Room room = mapper.toEntity(request);
        room.setHotel(hotel);

        // A duplicate (hotel_id, room_number) is rejected by the uq_room_per_hotel
        // constraint, surfacing as DataIntegrityViolationException -> 409 Conflict.
        return mapper.toResponse(roomRepo.save(room));
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoom(Long hotelId, Long roomId) {
        return mapper.toResponse(findOwnedRoom(hotelId, roomId));
    }

    @Transactional(readOnly = true)
    public Page<RoomResponse> listRooms(Long hotelId, Pageable pageable) {
        if (!hotelRepo.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel", hotelId);
        }
        // Page.map keeps the paging metadata (total elements, total pages) and converts
        // only the content — Page<Room> becomes Page<RoomResponse>.
        return roomRepo.findByHotelId(hotelId, pageable).map(mapper::toResponse);
    }

    @Transactional
    public RoomResponse updateRoom(Long hotelId, Long roomId, UpdateRoomRequest request) {
        Room room = findOwnedRoom(hotelId, roomId);
        mapper.updateEntity(request, room);
        // No save() needed: inside a transaction Hibernate dirty-checks the managed entity
        // and issues the UPDATE at commit. Left implicit on purpose — see how-it-all-works 10.3.
        return mapper.toResponse(room);
    }

    @Transactional
    public void deleteRoom(Long hotelId, Long roomId) {
        roomRepo.delete(findOwnedRoom(hotelId, roomId));
    }

    /** One place that enforces "this room belongs to this hotel". */
    private Room findOwnedRoom(Long hotelId, Long roomId) {
        return roomRepo.findByIdAndHotelId(roomId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", roomId));
    }
}
