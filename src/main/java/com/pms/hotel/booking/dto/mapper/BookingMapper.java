package com.pms.hotel.booking.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.pms.hotel.booking.Booking;
import com.pms.hotel.booking.dto.BookingResponse;
import com.pms.hotel.booking.dto.CreateBookingRequest;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface BookingMapper {
    
    @Mapping(target = "hotel", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "guest", ignore = true)
    Booking toEntity(CreateBookingRequest request);

    @Mapping(target = "hotelId", source = "hotel.id")
    @Mapping(target = "guestId", source = "guest.id")
    @Mapping(target = "roomId",  source = "room.id")
    BookingResponse toResponse(Booking booking);
}
