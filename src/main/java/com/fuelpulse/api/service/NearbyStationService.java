package com.fuelpulse.api.service;

import com.fuelpulse.api.config.GeoProperties;
import com.fuelpulse.api.repository.GasStationRepository;
import com.fuelpulse.api.dto.response.NearbyStationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NearbyStationService {

    private final GasStationRepository repository;
    private final GeoProperties geo;

    public NearbyStationService(GasStationRepository repository, GeoProperties geo) {
        this.repository = repository;
        this.geo = geo;
    }

    /**
     * Finds stations near a point.
     *
     * <p>Coordinate ranges are enforced at the controller, where bean validation
     * produces a clean 400 and documents itself in the OpenAPI description. The
     * radius is checked here instead, because the limit comes from configuration
     * and the rule should hold for any caller of this service, not only HTTP.
     *
     * <p>An over-large radius is <strong>rejected</strong>, not clamped. Silently
     * returning results for a different radius than was asked for gives the caller
     * no way to know the limit exists, and turns a security boundary into a
     * surprise. Refusing makes the boundary explicit.
     */
    @Transactional(readOnly = true)
    public List<NearbyStationResponse> findNearby(double latitude, double longitude, int radiusMeters) {
        if (radiusMeters > geo.maxRadiusMeters()) {
            throw new RadiusExceededException(radiusMeters, geo.maxRadiusMeters());
        }

        return repository.findNearby(latitude, longitude, radiusMeters, geo.maxResults())
                .stream()
                .map(NearbyStationResponse::from)
                .toList();
    }

    /**
     * Raised when a caller asks for a radius above the configured ceiling.
     *
     * <p>Carries the limit deliberately: telling the caller the maximum is useful
     * and reveals nothing sensitive, since the value is a published API constraint
     * rather than an internal detail.
     */
    public static class RadiusExceededException extends RuntimeException {
        private final int requested;
        private final int maximum;

        public RadiusExceededException(int requested, int maximum) {
            super("Requested radius exceeds the maximum allowed");
            this.requested = requested;
            this.maximum = maximum;
        }

        public int getRequested() {
            return requested;
        }

        public int getMaximum() {
            return maximum;
        }
    }
}