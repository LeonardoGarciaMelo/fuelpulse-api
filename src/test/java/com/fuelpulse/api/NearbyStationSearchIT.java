package com.fuelpulse.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// Testcontainers 2.0 relocated container classes to org.testcontainers.<module>.
// Older guides import org.testcontainers.containers.PostgreSQLContainer, which no
// longer exists.
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

/**
 * Proximity search against a real PostGIS instance.
 *
 * <p>An in-memory database cannot run {@code ST_DWithin}, so testing this against
 * H2 would only prove that the mock behaves like the mock. The whole risk in this
 * feature lives in the SQL.
 */
@SpringBootTest(properties = {
        // application.yml intentionally has no defaults for these, so the
        // application refuses to start without them. Supplying them here keeps that
        // property meaningful in production while letting the context start in tests.
        "security.jwt.secret-key=test-only-not-a-real-secret",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "security.geo.max-radius-meters=50000",
        "security.geo.max-results=100"
})
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("GET /api/v1/stations/nearby")
class NearbyStationSearchIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                    .asCompatibleSubstituteFor("postgres"));

    // Praça da Sé, São Paulo. Any fixed point works; using a real one makes the
    // expected distances checkable by hand against a map.
    private static final double REF_LAT = -23.5505;
    private static final double REF_LON = -46.6333;

    private static final double METRES_PER_DEGREE_LAT = 110_760.0;

    private static final double ONE_KM_NORTH    = REF_LAT + (1_000.0  / METRES_PER_DEGREE_LAT);
    private static final double THREE_KM_NORTH  = REF_LAT + (3_000.0  / METRES_PER_DEGREE_LAT);
    private static final double TWENTY_KM_NORTH = REF_LAT + (20_000.0 / METRES_PER_DEGREE_LAT);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM gas_stations");
        insertStation("Near Station", "BrandA", ONE_KM_NORTH, REF_LON);
        insertStation("Middle Station", "BrandB", THREE_KM_NORTH, REF_LON);
        insertStation("Far Station", "BrandC", TWENTY_KM_NORTH, REF_LON);
    }

    private void insertStation(String name, String brand, double lat, double lon) {
        jdbc.update("""
                INSERT INTO gas_stations (name, brand, address, city, state, location)
                VALUES (?, ?, 'Test address', 'Sao Paulo', 'SP',
                        ST_SetSRID(ST_MakePoint(?, ?), 4326))
                """, name, brand, lon, lat);
    }

    @Test
    @DisplayName("returns only stations inside the radius, nearest first")
    void returnsStationsWithinRadius() throws Exception {
        mockMvc.perform(get("/api/v1/stations/nearby")
                        .param("lat", String.valueOf(REF_LAT))
                        .param("lon", String.valueOf(REF_LON))
                        .param("radius", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Near Station")))
                .andExpect(jsonPath("$[1].name", is("Middle Station")));
    }

    /**
     * The regression test for the {@code ::geography} casts.
     *
     * <p>If a cast is removed, PostGIS measures in degrees: the reported distance
     * collapses to roughly 0.009 instead of 1000, and every station in the table
     * falls inside a 5000-unit radius. Asserting a real metre value is what makes
     * that mistake loud instead of silent.
     */
    @Test
    @DisplayName("reports distance in metres, not degrees")
    void reportsDistanceInMetres() throws Exception {
        mockMvc.perform(get("/api/v1/stations/nearby")
                        .param("lat", String.valueOf(REF_LAT))
                        .param("lon", String.valueOf(REF_LON))
                        .param("radius", "5000"))
                .andExpect(status().isOk())
                // Tolerance covers the spheroid calculation differing slightly from
                // the flat approximation used to place the fixtures.
                .andExpect(jsonPath("$[0].distanceMeters", both(greaterThan(980)).and(lessThan(1020))))
                .andExpect(jsonPath("$[1].distanceMeters", both(greaterThan(2960)).and(lessThan(3040))));
    }

    @Test
    @DisplayName("excludes stations beyond the radius")
    void excludesStationsOutsideRadius() throws Exception {
        mockMvc.perform(get("/api/v1/stations/nearby")
                        .param("lat", String.valueOf(REF_LAT))
                        .param("lon", String.valueOf(REF_LON))
                        .param("radius", "1500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Near Station")));
    }

    @Test
    @DisplayName("rejects latitude outside the valid range")
    void rejectsOutOfRangeLatitude() throws Exception {
        mockMvc.perform(get("/api/v1/stations/nearby")
                        .param("lat", "91.0")
                        .param("lon", String.valueOf(REF_LON))
                        .param("radius", "5000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Invalid request parameters")));
    }

    @Test
    @DisplayName("rejects a radius above the configured maximum")
    void rejectsOversizedRadius() throws Exception {
        mockMvc.perform(get("/api/v1/stations/nearby")
                        .param("lat", String.valueOf(REF_LAT))
                        .param("lon", String.valueOf(REF_LON))
                        .param("radius", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Radius too large")))
                .andExpect(jsonPath("$.maximumMeters", is(50000)));
    }

    @Test
    @DisplayName("does not leak internal detail on error responses")
    void errorResponsesCarryNoInternalDetail() throws Exception {
        mockMvc.perform(get("/api/v1/stations/nearby")
                        .param("lat", "not-a-number")
                        .param("lon", String.valueOf(REF_LON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail",
                        is("One or more query parameters are missing or of the wrong type")));
    }

    @Test
    @DisplayName("denies requests to endpoints with no explicit rule")
    void deniesUnmappedEndpointsByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/stations"))
                .andExpect(status().is4xxClientError());
    }
}