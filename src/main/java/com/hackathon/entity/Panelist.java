package com.hackathon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "panelists")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Panelist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    private String domain;

    @Enumerated(EnumType.STRING)
    private PanelistAvailability availability;

    /** Populated only when availability is CUSTOM, e.g. "14:00-17:00" */
    private String customAvailabilityTime;
}
