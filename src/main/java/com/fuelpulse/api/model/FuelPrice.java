package com.fuelpulse.api.model;

import com.fuelpulse.api.model.enums.FuelType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "fuel_prices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FuelPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private GasStation station;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 30)
    private FuelType fuelType;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by")
    private User reportedBy;

    @CreationTimestamp
    @Column(name = "reported_at", updatable = false)
    private ZonedDateTime reportedAt;
}
