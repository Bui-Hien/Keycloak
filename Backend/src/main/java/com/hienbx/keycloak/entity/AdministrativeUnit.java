package com.hienbx.keycloak.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tbl_administrative_unit", indexes = {
        @Index(name = "idx_admin_unit_query", columnList = "country_id, level, parent_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdministrativeUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_id", nullable = false)
    private String countryId;

    @Column(name = "level", nullable = false)
    private Integer level; // 1, 2, 3...

    @Column(name = "parent_id")
    private Long parentId; // ID of the parent unit, null if level = 1

    @Column(name = "name", nullable = false)
    private String name; // e.g. "Hà Nội", "Quận Cầu Giấy"
}
