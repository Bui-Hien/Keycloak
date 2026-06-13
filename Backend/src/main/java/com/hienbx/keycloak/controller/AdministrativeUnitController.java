package com.hienbx.keycloak.controller;

import com.hienbx.keycloak.entity.AdministrativeUnit;
import com.hienbx.keycloak.entity.Country;
import com.hienbx.keycloak.entity.CountryAdministrativeSchema;
import com.hienbx.keycloak.service.AdministrativeUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdministrativeUnitController {

    private final AdministrativeUnitService unitService;

    @GetMapping("/countries")
    public ResponseEntity<List<Country>> getAllCountries() {
        return ResponseEntity.ok(unitService.getAllCountries());
    }

    @GetMapping("/countries/{countryId}/levels")
    public ResponseEntity<List<CountryAdministrativeSchema>> getSchemaByCountryId(@PathVariable String countryId) {
        return ResponseEntity.ok(unitService.getSchemaByCountryId(countryId));
    }

    @GetMapping("/administrative-units")
    public ResponseEntity<List<AdministrativeUnit>> getUnits(
            @RequestParam String countryId,
            @RequestParam Integer level,
            @RequestParam(required = false) Long parentId
    ) {
        return ResponseEntity.ok(unitService.getUnits(countryId, level, parentId));
    }

    @PostMapping("/administrative-units/fake-data")
    public ResponseEntity<Map<String, String>> generateFakeData() {
        unitService.generateFakeData();
        return ResponseEntity.ok(Map.of("message", "Generated administrative mock data for VN and US successfully."));
    }
}
