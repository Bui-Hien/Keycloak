package com.hienbx.keycloak.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tbl_country_administrative_schema", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"country_id", "level"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountryAdministrativeSchema {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(name = "level", nullable = false)
    private Integer level; // 1, 2, 3...

    @Column(name = "level_name", nullable = false)
    private String levelName; // e.g. "Tỉnh / Thành phố", "State"
}
