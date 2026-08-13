package com.pms.hotel.profile;
import com.pms.shared.error.*;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pms.hotel.profile.dto.CreateHotelRequest;
import com.pms.hotel.profile.dto.HotelResponse;
import com.pms.hotel.profile.dto.RoomsOfHotel;
import com.pms.hotel.profile.dto.UpdateHotelRequest;
import com.pms.hotel.profile.dto.mapper.HotelMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HotelService {
    
    private final HotelRepository repo;
    private final HotelMapper mapper;

    @Transactional
    public HotelResponse createHotel(CreateHotelRequest request){
        Hotel hotel = mapper.toEntity(request);
        Hotel saved = repo.save(hotel);
        HotelResponse response= mapper.toResponse(saved);
        return response;
    }

    @Transactional(readOnly = true)
    public HotelResponse getHotel(Long id){
        Hotel hotel = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hotel", id));
        return mapper.toResponse(hotel);
    }

    @Transactional
    public HotelResponse updateHotel(Long id, UpdateHotelRequest request){
        Hotel entity = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hotel", id));
        mapper.updateEntity(request, entity);
        return mapper.toResponse(repo.save(entity));
    }

    @Transactional
    public void deleteHotel(Long id){
        repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Hotel", id));
        repo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public RoomsOfHotel getHotelRooms(Long id){
        // Run the JOIN FETCH query directly. 
        // This returns Optional<Hotel> with the 'rooms' list ALREADY populated.
        // Even if there are no room, since we have used Left join, the Hotel should be present in the query result, 
        // if not present, then it only means the hotel iself is not present and thats' when this throw runs
        Hotel entity = repo.findHotelRoomWithID(id).orElseThrow(() -> new ResourceNotFoundException("Hotel",id));
        return mapper.toRoomsOfHotel(entity);
    }
}
