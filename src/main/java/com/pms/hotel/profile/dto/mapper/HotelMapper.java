package com.pms.hotel.profile.dto.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.pms.hotel.profile.Hotel;
import com.pms.hotel.profile.dto.CreateHotelRequest;
import com.pms.hotel.profile.dto.HotelResponse;
import com.pms.hotel.profile.dto.UpdateHotelRequest;

@Mapper( 
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface HotelMapper {

    Hotel toEntity(CreateHotelRequest request);

    HotelResponse toResponse(Hotel hotel);

    @Mapping(target = "id", ignore = true) //optional
    void updateEntity(UpdateHotelRequest request, @MappingTarget Hotel hotel);
}