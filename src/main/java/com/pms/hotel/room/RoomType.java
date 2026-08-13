package com.pms.hotel.room;

/**
 * Mirrors the Postgres type:  CREATE TYPE room_type AS ENUM ('SINGLE', 'DOUBLE', 'SUITE');
 *
 * The constant NAMES must match the Postgres labels exactly — that is what makes the
 * mapping possible at all.
 */
public enum RoomType {
    SINGLE,
    DOUBLE,
    SUITE
}
