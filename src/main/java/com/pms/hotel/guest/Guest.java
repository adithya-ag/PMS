package com.pms.hotel.guest;

import com.pms.hotel.profile.Hotel;
import com.pms.shared.model.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Maps to hotel_guests. Same shape as Room — nothing new here.
 *
 * Guests are stored PER HOTEL by design (a PRD decision: isolation and privacy).
 * The same person staying at two hotels is two rows, and neither hotel can see
 * the other's record.
 *
 * Note `email` is the first genuinely NULLABLE business column in the project —
 * the schema allows it, so the entity must too, and so must the validation.
 */
@Entity
@Table(name = "hotel_guests")
@Getter
@Setter
@ToString(callSuper = true)
public class Guest extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone", nullable = false)
    private String phone;

    // nullable in the schema — email is optional for a walk-in guest
    @Column(name = "email")
    private String email;
}
