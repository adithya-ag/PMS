package com.pms.hotel.guest.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.pms.hotel.guest.Guest;
import com.pms.hotel.guest.dto.CreateGuestRequest;
import com.pms.hotel.guest.dto.GuestResponse;
import com.pms.hotel.guest.dto.UpdateGuestRequest;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface GuestMapper {

    // The service resolves and sets the Hotel; the mapper must not invent one.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    Guest toEntity(CreateGuestRequest request);

    // Flatten the relationship: guest.getHotel().getId() -> response.hotelId()
    @Mapping(source = "hotel.id", target = "hotelId")
    GuestResponse toResponse(Guest guest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hotel", ignore = true)
    void updateEntity(UpdateGuestRequest request, @MappingTarget Guest guest);
}
