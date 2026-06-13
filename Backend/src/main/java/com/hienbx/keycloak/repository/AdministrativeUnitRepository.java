package com.hienbx.keycloak.repository;

import com.hienbx.keycloak.entity.AdministrativeUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdministrativeUnitRepository extends JpaRepository<AdministrativeUnit, Long> {

    @Query("SELECT u FROM AdministrativeUnit u WHERE u.countryId = :countryId AND u.level = :level AND (:parentId IS NULL AND u.parentId IS NULL OR :parentId IS NOT NULL AND u.parentId = :parentId) ORDER BY u.name ASC")
    List<AdministrativeUnit> findUnits(
            @Param("countryId") String countryId,
            @Param("level") Integer level,
            @Param("parentId") Long parentId
    );
}
