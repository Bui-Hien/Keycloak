package com.hienbx.keycloak.repository;

import com.hienbx.keycloak.entity.CountryAdministrativeSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CountryAdministrativeSchemaRepository extends JpaRepository<CountryAdministrativeSchema, Long> {

    @Query("SELECT schema FROM CountryAdministrativeSchema schema WHERE schema.country.id = :countryId ORDER BY schema.level ASC")
    List<CountryAdministrativeSchema> findByCountryId(@Param("countryId") String countryId);
}
