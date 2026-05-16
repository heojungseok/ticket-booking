package com.ticketbooking.performance.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "performances")
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    private String description;
    private String venueName;
    private LocalDateTime startsAt;

    @Enumerated(EnumType.STRING)
    private SaleType saleType;

    private LocalDateTime saleStartsAt;
    private LocalDateTime saleEndsAt;

    @Enumerated(EnumType.STRING)
    private PerformanceStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Performance() {
    }

    public Performance(
            String title,
            String description,
            String venueName,
            LocalDateTime startsAt,
            SaleType saleType,
            LocalDateTime saleStartsAt,
            LocalDateTime saleEndsAt,
            PerformanceStatus status
    ) {
        this.title = title;
        this.description = description;
        this.venueName = venueName;
        this.startsAt = startsAt;
        this.saleType = saleType;
        this.saleStartsAt = saleStartsAt;
        this.saleEndsAt = saleEndsAt;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public SaleType getSaleType() {
        return saleType;
    }

    public PerformanceStatus getStatus() {
        return status;
    }
}
