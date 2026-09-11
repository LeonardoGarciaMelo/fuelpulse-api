package com.fuelpulse.api.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bounds for geospatial queries, bound from {@code security.geo.*}.
 *
 * <p>These live under {@code security} rather than a domain prefix on purpose.
 * An uncapped proximity search is not a performance problem, it is a data
 * exfiltration endpoint: given a large enough radius, one request returns the
 * entire station table. The cap is the control.
 *
 * <p>Validation here means a misconfigured deployment fails at startup instead of
 * running with a limit of zero or a negative radius.
 *
 * @param maxRadiusMeters largest radius a caller may request
 * @param maxResults      hard ceiling on rows returned, independent of radius
 */
@Validated
@ConfigurationProperties(prefix = "security.geo")
public record GeoProperties(
        @Positive @Max(200_000) int maxRadiusMeters,
        @Positive @Max(500) int maxResults
) {
}