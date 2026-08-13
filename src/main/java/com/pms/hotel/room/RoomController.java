package com.pms.hotel.room;

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
import org.springframework.web.bind.annotation.RestController;

import com.pms.hotel.room.dto.CreateRoomRequest;
import com.pms.hotel.room.dto.RoomResponse;
import com.pms.hotel.room.dto.UpdateRoomRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * NESTED URL:  /api/hotels/{hotelId}/rooms
 *
 * A room only exists inside a hotel, so the URL says so. This is the standard REST shape
 * for a child resource, and it makes the tenant explicit in every single request.
 *
 * In phase 8 the tenant will come from the JWT instead. The path can stay — the security
 * layer will then assert "the token's hotelId matches the path's hotelId", which is a
 * stronger check than either alone.
 */
@RestController
@RequestMapping("/api/hotels/{hotelId}/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService service;

    // 201 Created + the new room
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @PathVariable Long hotelId,
            @Valid @RequestBody CreateRoomRequest request) {

        RoomResponse response = service.createRoom(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 200 OK + one room
    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(
            @PathVariable Long hotelId,
            @PathVariable Long roomId) {

        return ResponseEntity.ok(service.getRoom(hotelId, roomId));
    }

    /**
     * 200 OK + a PAGE of rooms.
     *
     * Spring builds the Pageable from the query string automatically:
     *     GET /api/hotels/1/rooms?page=0&size=20&sort=floor,asc
     *
     * @PageableDefault supplies the values when the client sends none. Without a default,
     * an unbounded list on a large table loads every row into the heap.
     */
    @GetMapping
    public ResponseEntity<Page<RoomResponse>> listRooms(
            @PathVariable Long hotelId,
            @PageableDefault(size = 20, sort = "roomNumber", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(service.listRooms(hotelId, pageable));
    }

    // 200 OK + the updated room
    @PutMapping("/{roomId}")
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable Long hotelId,
            @PathVariable Long roomId,
            @Valid @RequestBody UpdateRoomRequest request) {

        return ResponseEntity.ok(service.updateRoom(hotelId, roomId, request));
    }

    // 204 No Content, empty body
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable Long hotelId,
            @PathVariable Long roomId) {

        service.deleteRoom(hotelId, roomId);
        return ResponseEntity.noContent().build();
    }
}
