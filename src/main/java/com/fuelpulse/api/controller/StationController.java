package com.fuelpulse.api.controller;

import com.fuelpulse.api.service.NearbyStationService;
import com.fuelpulse.api.dto.response.NearbyStationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stations")
@Validated
@Tag(name = "Stations", description = "Gas station lookup")
public class StationController {

    private final NearbyStationService service;

    public StationController(NearbyStationService service) {
        this.service = service;
    }

    /**
     * Proximity search.
     *
     * <p>Coordinates are bounded to the valid WGS84 ranges. This is input validation
     * first and foremost: without it, out-of-range values reach PostGIS, which either
     * errors or produces nonsense, and the failure surfaces as a 500 carrying database
     * wording rather than a 400 the caller can act on.
     *
     * <p>{@code @Validated} on the class enables constraint checking on these
     * parameters; violations become {@code ConstraintViolationException} and are
     * translated by {@link ApiExceptionHandler}.
     */
    @GetMapping("/nearby")
    @Operation(summary = "Find gas stations within a radius, nearest first")
    public List<NearbyStationResponse> nearby(
            @RequestParam
            @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
            @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
            double lat,

            @RequestParam
            @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
            @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
            double lon,

            @RequestParam(defaultValue = "5000")
            @Positive(message = "radius must be greater than zero")
            int radius
    ) {
        return service.findNearby(lat, lon, radius);
    }
}