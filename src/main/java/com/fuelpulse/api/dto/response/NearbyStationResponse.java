package com.fuelpulse.api.dto.response;

import com.fuelpulse.api.repository.GasStationRepository.NearbyStationProjection;

import java.util.UUID;

/**
 * A single result of a proximity search.
 *
 * <p>A dedicated response type, not the {@code GasStation} entity. Serializing
 * entities directly is how fields nobody meant to publish end up in an API
 * response, and it couples the public contract to the database schema — renaming a
 * column would silently change the API.
 *
 * @param distanceMeters straight-line distance on the spheroid, rounded to whole
 *                       metres; sub-metre precision implies an accuracy this data
 *                       does not have
 */
public record NearbyStationResponse(
        UUID id,
        String name,
        String brand,
        String address,
        String city,
        String state,
        double latitude,
        double longitude,
        long distanceMeters
) {
    public static NearbyStationResponse from(NearbyStationProjection p) {
        return new NearbyStationResponse(
                p.getId(),
                p.getName(),
                p.getBrand(),
                p.getAddress(),
                p.getCity(),
                p.getState(),
                p.getLatitude(),
                p.getLongitude(),
                Math.round(p.getDistanceMeters())
        );
    }
}