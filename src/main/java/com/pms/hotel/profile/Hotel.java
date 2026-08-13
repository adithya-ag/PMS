package com.pms.hotel.profile;

import java.util.List;

import com.pms.hotel.room.Room;
import com.pms.shared.model.Auditable;

import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "hotels")
@Getter
@Setter
@ToString(callSuper = true)
public class Hotel extends Auditable {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "phone", nullable = false, unique = true)
    private String phone;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "pincode", nullable = false)
    private String pincode;


    /**
     * Defines the one-to-many relationship with Room.
     * 
     * WHY THIS EXISTS HERE (vs. querying Room repository):
     * 1. Encapsulation: Keeps the relationship logic within the domain model.
     * 2. Performance (N+1 Prevention): Allows the use of 'JOIN FETCH' in JPQL.
     *    This enables fetching the Hotel and ALL its Rooms in a SINGLE SQL query.
     *    Without this field, we would need separate queries and manual assembly in the Service layer,
     *    which causes performance issues (N+1) when loading lists of Hotels.
     * 3. Convenience: Provides direct navigation (hotel.getRooms()) when the full graph is needed.
     * 
     * NOTE: Fetch is LAZY by default to avoid loading rooms when only Hotel details are needed.
     *       Use a specific Repository query with 'JOIN FETCH' when rooms are required.
     */
    @OneToMany(mappedBy = "hotel", fetch = FetchType.LAZY)
    private List<Room> rooms;

}
