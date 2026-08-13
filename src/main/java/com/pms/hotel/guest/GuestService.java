package com.pms.hotel.guest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.pms.hotel.guest.dto.CreateGuestRequest;
import com.pms.hotel.guest.dto.GuestResponse;
import com.pms.hotel.guest.dto.UpdateGuestRequest;
import com.pms.hotel.guest.dto.mapper.GuestMapper;
import com.pms.hotel.profile.Hotel;
import com.pms.hotel.profile.HotelRepository;
import com.pms.shared.error.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Same shape as RoomService. Every method is scoped by hotelId.
 */
@Service
@RequiredArgsConstructor
public class GuestService {

    private final GuestRepository guestRepo;
    private final HotelRepository hotelRepo;
    private final GuestMapper mapper;

    @Transactional
    public GuestResponse createGuest(Long hotelId, CreateGuestRequest request) {
        Hotel hotel = hotelRepo.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", hotelId));

        Guest guest = mapper.toEntity(request);
        guest.setHotel(hotel);

        return mapper.toResponse(guestRepo.save(guest));
    }

    @Transactional(readOnly = true)
    public GuestResponse getGuest(Long hotelId, Long guestId) {
        return mapper.toResponse(findOwnedGuest(hotelId, guestId));
    }

    /**
     * Lists guests, optionally filtered by phone.
     *
     * The phone filter is the "returning guest" lookup — the receptionist types a number
     * and finds the existing record instead of creating a duplicate. It rides on
     * idx_guests_phone (hotel_id, phone).
     *
     * One method serving both cases keeps the API simple: no phone means list everything.
     */
    @Transactional(readOnly = true)
    public Page<GuestResponse> listGuests(Long hotelId, String phone, Pageable pageable) {
        if (!hotelRepo.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel", hotelId);
        }

        Page<Guest> page = StringUtils.hasText(phone)
                ? guestRepo.findByHotelIdAndPhone(hotelId, phone, pageable)
                : guestRepo.findByHotelId(hotelId, pageable);

        return page.map(mapper::toResponse);
    }

    @Transactional
    public GuestResponse updateGuest(Long hotelId, Long guestId, UpdateGuestRequest request) {
        Guest guest = findOwnedGuest(hotelId, guestId);
        mapper.updateEntity(request, guest);
        // No save() — dirty checking writes the UPDATE at commit.
        return mapper.toResponse(guest);
    }

    @Transactional
    public void deleteGuest(Long hotelId, Long guestId) {
        guestRepo.delete(findOwnedGuest(hotelId, guestId));
    }

    /** One place that enforces "this guest belongs to this hotel". */
    private Guest findOwnedGuest(Long hotelId, Long guestId) {
        return guestRepo.findByIdAndHotelId(guestId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest", guestId));
    }
}
