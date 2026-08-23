package com.pms.hotel.booking;


import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pms.hotel.booking.dto.BookingResponse;
import com.pms.hotel.booking.dto.CreateBookingRequest;
import com.pms.hotel.booking.dto.mapper.BookingMapper;
import com.pms.hotel.guest.Guest;
import com.pms.hotel.guest.GuestRepository;
import com.pms.hotel.profile.Hotel;
import com.pms.hotel.profile.HotelRepository;
import com.pms.hotel.room.Room;
import com.pms.hotel.room.RoomRepository;
import com.pms.shared.error.BookingConflictException;
import com.pms.shared.error.InvalidDateRangeException;
import com.pms.shared.error.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository repo;
    private final BookingMapper mapper;
    private final HotelRepository hotelRepo;
    private final GuestRepository guestRepo;
    private final RoomRepository roomRepo;
    
    @Transactional
    public BookingResponse createBooking(Long hotelId, CreateBookingRequest request){
        
        Hotel hotel = hotelRepo.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", hotelId));
        Guest guest = guestRepo.findByIdAndHotelId(request.guestId(), hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest", request.guestId()));;

        if(!request.checkOutDate().isAfter(request.checkInDate())){
            throw new InvalidDateRangeException();
        }

        Room room;
        
        // There are two sinirio here, the user can send us the room that they are looking for, or otherwise we check based on thet
        // type they are looking for and we see the availablity and assign the room if available
        if(request.roomId() != null){

            room = roomRepo.findForUpdateByIdAndHotelId(request.roomId(), hotelId).orElseThrow(() ->
                new ResourceNotFoundException("Room")
            );
            if(repo.existsOverlapping(request.roomId(), request.checkInDate(), request.checkOutDate())){
                throw new BookingConflictException();
            };

        }else{

            List<Room> available = repo.findAvailableRooms(hotelId, request.roomType(), request.checkInDate(), request.checkOutDate());

            if(available.isEmpty()){
                throw new BookingConflictException("No rooms available for the selected type and dates");
            }

            room = available.get(0);
        }
        // try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Booking booking = mapper.toEntity(request);
        booking.setHotel(hotel);
        booking.setRoom(room);
        booking.setGuest(guest);
        booking.setStatus(BookingStatus.CONFIRMED);
        
        return mapper.toResponse(repo.save(booking));
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> listBooking(Long hotelId, Pageable page){
        hotelRepo.findById(hotelId).orElseThrow(() -> new ResourceNotFoundException("Hotel"));
        return repo.findByHotelId(hotelId, page).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long hotelId, Long bookingId){
        hotelRepo.findById(hotelId).orElseThrow(() -> new ResourceNotFoundException("Hotel"));
        return mapper.toResponse(repo.findByIdAndHotelId(bookingId, hotelId).orElseThrow(
            () -> new ResourceNotFoundException("Booking")
        ));
    }

    @Transactional
    public BookingResponse cancelBooking(Long hotelId, Long bookingId){
        Booking booking = repo.findByIdAndHotelId(bookingId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking"));
        booking.setStatus(BookingStatus.CANCELLED);
        return mapper.toResponse(repo.save(booking));
    }
}
