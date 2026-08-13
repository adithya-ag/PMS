package com.pms.hotel.profile.dto.mapper;

import java.lang.annotation.Target;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.pms.hotel.profile.Hotel;
import com.pms.hotel.profile.dto.CreateHotelRequest;
import com.pms.hotel.profile.dto.HotelResponse;
import com.pms.hotel.profile.dto.RoomSummary;
import com.pms.hotel.profile.dto.RoomsOfHotel;
import com.pms.hotel.profile.dto.UpdateHotelRequest;
import com.pms.hotel.room.Room;

@Mapper( 
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface HotelMapper {

    Hotel toEntity(CreateHotelRequest request);

    HotelResponse toResponse(Hotel hotel);

    @Mapping(target = "id", ignore = true) //optional
    void updateEntity(UpdateHotelRequest request, @MappingTarget Hotel hotel);


    /**
     * Maps a Hotel entity to a RoomsOfHotel DTO.
     * 
     * MECHANISM:
     * - MapStruct automatically detects that the target field 'rooms' is a List<RoomSummary>
     *   while the source field is a List<Room>.
     * - It generates a loop that calls 'toRoomSummary(Room)' for each entity in the list.
     * 
     * ARCHITECTURAL DECISION:
     * - The query resides in the Hotel module because the Hotel entity owns the relationship 
     *   (@OneToMany). This allows us to use 'JOIN FETCH' to solve the N+1 problem, 
     *   retrieving the Hotel and all its Rooms in a SINGLE database query.
     * - Querying from the Room module would require manual assembly in the Service layer 
     *   and risks performance issues when loading multiple hotels.
     */
    RoomsOfHotel toRoomsOfHotel(Hotel hotel);

    RoomSummary toRoomSummary(Room room);
}