package com.pms.hotel.room.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.pms.hotel.room.Room;
import com.pms.hotel.room.dto.CreateRoomRequest;
import com.pms.hotel.room.dto.RoomResponse;
import com.pms.hotel.room.dto.UpdateRoomRequest;

/**
 * Compile-time mapper. Look in target/generated-sources/annotations/ after a build
 * to read the generated RoomMapperImpl — it is plain getter/setter code, no reflection.
 *
 * NOTE: this assumes Room models the relationship as an object reference:
 *
 *     @ManyToOne  private Hotel hotel;
 *
 * If you instead model it as a plain `Long hotelId`, delete the two @Mapping lines
 * that mention `hotel` and MapStruct will match the names directly.
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface RoomMapper {

    // The service resolves and sets the Hotel — the mapper must not invent one.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    Room toEntity(CreateRoomRequest request);

    // Flatten the relationship: room.getHotel().getId()  ->  response.hotelId()
    @Mapping(source = "hotel.id", target = "hotelId")
    RoomResponse toResponse(Room room);

    // A room can never change hotels, and its id is fixed.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    void updateEntity(UpdateRoomRequest request, @MappingTarget Room room);
}
