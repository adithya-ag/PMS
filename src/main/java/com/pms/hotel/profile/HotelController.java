package com.pms.hotel.profile;


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

import com.pms.hotel.profile.dto.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {
    
    private final HotelService service;

    // 1. CREATE (Returns 201 Created + Data)
    @PostMapping
    public ResponseEntity<HotelResponse> createHotel(@Valid @RequestBody CreateHotelRequest request){
        HotelResponse response = service.createHotel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    

    // 2. READ (Returns 200 OK + Data)
    // ID comes from URL: /api/hotels/5
    @GetMapping("/{id}")
    public ResponseEntity<HotelResponse> getHotel(@PathVariable Long id){
        HotelResponse response = service.getHotel(id);
        return ResponseEntity.ok(response);
    }

    // how to work on RBAC for the method update and delete Hotel

    @PutMapping("/{id}")
    public ResponseEntity<HotelResponse> updateHotel(@PathVariable Long id,@Valid @RequestBody UpdateHotelRequest request){
        return ResponseEntity.ok(service.updateHotel(id, request));
    }


    // 4. DELETE (Returns 204 No Content + NO Body)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHotel(@PathVariable Long id){
        service.deleteHotel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/get/rooms") //is that correct way of creating url?
    public ResponseEntity<RoomsOfHotel> getHotelRooms(@PathVariable Long id){
        return ResponseEntity.ok(service.getHotelRooms(id)); 
    }

}
