package com.fuelpulse.api.repository;

import com.fuelpulse.api.model.GasStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GasStationRepository extends JpaRepository<GasStation, UUID> {

    /**
     * Stations within {@code radiusMeters} of a point, nearest first.
     *
     * <p><strong>The {@code ::geography} casts are the whole point of this query.</strong>
     * The column is {@code GEOMETRY(Point, 4326)}, and in SRID 4326 distance is
     * measured in degrees. Calling {@code ST_DWithin} on the raw geometry with a
     * radius of 5000 asks for everything within 5000 <em>degrees</em> — the entire
     * table, returned without error. Casting to {@code geography} switches the
     * calculation to metres on the spheroid.
     *
     * <p>This is the failure mode the integration test exists to catch: it asserts a
     * known distance in metres, so removing a cast turns the test red instead of
     * quietly breaking the endpoint.
     *
     * <p>The cast requires the functional index added in {@code V2__phase1_indexes.sql};
     * the geometry index from V1 cannot answer a geography predicate.
     *
     * <p>Aliases are double-quoted so PostgreSQL preserves their case. Unquoted
     * identifiers fold to lowercase, which breaks the mapping onto
     * {@code getDistanceMeters()}.
     */
    @Query(value = """
            SELECT gs.id                                   AS "id",
                   gs.name                                 AS "name",
                   gs.brand                                AS "brand",
                   gs.address                              AS "address",
                   gs.city                                 AS "city",
                   gs.state                                AS "state",
                   ST_Y(gs.location)                       AS "latitude",
                   ST_X(gs.location)                       AS "longitude",
                   ST_Distance(
                       gs.location::geography,
                       ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
                   )                                       AS "distanceMeters"
            FROM gas_stations gs
            WHERE ST_DWithin(
                      gs.location::geography,
                      ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                      :radiusMeters
                  )
            ORDER BY "distanceMeters"
            LIMIT :maxResults
            """, nativeQuery = true)
    List<NearbyStationProjection> findNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("maxResults") int maxResults
    );

    /** Read-only view of a station plus its computed distance from the query point. */
    interface NearbyStationProjection {
        UUID getId();

        String getName();

        String getBrand();

        String getAddress();

        String getCity();

        String getState();

        double getLatitude();

        double getLongitude();

        double getDistanceMeters();
    }
}