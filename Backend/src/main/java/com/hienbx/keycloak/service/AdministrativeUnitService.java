package com.hienbx.keycloak.service;

import com.hienbx.keycloak.entity.AdministrativeUnit;
import com.hienbx.keycloak.entity.Country;
import com.hienbx.keycloak.entity.CountryAdministrativeSchema;
import com.hienbx.keycloak.repository.AdministrativeUnitRepository;
import com.hienbx.keycloak.repository.CountryAdministrativeSchemaRepository;
import com.hienbx.keycloak.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdministrativeUnitService {

    private final CountryRepository countryRepository;
    private final CountryAdministrativeSchemaRepository schemaRepository;
    private final AdministrativeUnitRepository unitRepository;

    @Transactional(readOnly = true)
    public List<Country> getAllCountries() {
        return countryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CountryAdministrativeSchema> getSchemaByCountryId(String countryId) {
        return schemaRepository.findByCountryId(countryId);
    }

    @Transactional(readOnly = true)
    public List<AdministrativeUnit> getUnits(String countryId, Integer level, Long parentId) {
        return unitRepository.findUnits(countryId, level, parentId);
    }

    @Transactional
    public void generateFakeData() {
        // Clear old data to avoid duplication
        unitRepository.deleteAll();
        schemaRepository.deleteAll();
        countryRepository.deleteAll();

        // 1. Create Countries
        Country vn = Country.builder().id("VN").name("Việt Nam").build();
        Country us = Country.builder().id("US").name("United States").build();
        countryRepository.save(vn);
        countryRepository.save(us);

        // 2. Create Schemas
        schemaRepository.save(CountryAdministrativeSchema.builder().country(vn).level(1).levelName("Tỉnh / Thành phố").build());
        schemaRepository.save(CountryAdministrativeSchema.builder().country(vn).level(2).levelName("Quận / Huyện").build());
        schemaRepository.save(CountryAdministrativeSchema.builder().country(vn).level(3).levelName("Phường / Xã").build());

        schemaRepository.save(CountryAdministrativeSchema.builder().country(us).level(1).levelName("State (Bang)").build());
        schemaRepository.save(CountryAdministrativeSchema.builder().country(us).level(2).levelName("County (Hạt)").build());
        schemaRepository.save(CountryAdministrativeSchema.builder().country(us).level(3).levelName("City (Thành phố)").build());

        // 3. Create Administrative Units (Vietnam)
        // Level 1: Province
        AdministrativeUnit haNoi = unitRepository.save(AdministrativeUnit.builder().countryId("VN").level(1).parentId(null).name("Thành phố Hà Nội").build());
        AdministrativeUnit hcm = unitRepository.save(AdministrativeUnit.builder().countryId("VN").level(1).parentId(null).name("Thành phố Hồ Chí Minh").build());

        // Level 2: District
        AdministrativeUnit cauGiay = unitRepository.save(AdministrativeUnit.builder().countryId("VN").level(2).parentId(haNoi.getId()).name("Quận Cầu Giấy").build());
        AdministrativeUnit baDinh = unitRepository.save(AdministrativeUnit.builder().countryId("VN").level(2).parentId(haNoi.getId()).name("Quận Ba Đình").build());
        AdministrativeUnit q1 = unitRepository.save(AdministrativeUnit.builder().countryId("VN").level(2).parentId(hcm.getId()).name("Quận 1").build());
        AdministrativeUnit q3 = unitRepository.save(AdministrativeUnit.builder().countryId("VN").level(2).parentId(hcm.getId()).name("Quận 3").build());

        // Level 3: Ward
        unitRepository.save(AdministrativeUnit.builder().countryId("VN").level(3).parentId(cauGiay.getId()).name("Phường Dịch Vọng").build());
        unitRepository.save(AdministrativeUnit.builder().countryId("VN").level(3).parentId(cauGiay.getId()).name("Phường Nghĩa Tân").build());
        unitRepository.save(AdministrativeUnit.builder().countryId("VN").level(3).parentId(baDinh.getId()).name("Phường Kim Mã").build());
        unitRepository.save(AdministrativeUnit.builder().countryId("VN").level(3).parentId(q1.getId()).name("Phường Đa Kao").build());
        unitRepository.save(AdministrativeUnit.builder().countryId("VN").level(3).parentId(q3.getId()).name("Phường Võ Thị Sáu").build());

        // 4. Create Administrative Units (USA)
        // Level 1: State
        AdministrativeUnit california = unitRepository.save(AdministrativeUnit.builder().countryId("US").level(1).parentId(null).name("California").build());
        AdministrativeUnit newYork = unitRepository.save(AdministrativeUnit.builder().countryId("US").level(1).parentId(null).name("New York").build());

        // Level 2: County
        AdministrativeUnit laCounty = unitRepository.save(AdministrativeUnit.builder().countryId("US").level(2).parentId(california.getId()).name("Los Angeles County").build());
        AdministrativeUnit orangeCounty = unitRepository.save(AdministrativeUnit.builder().countryId("US").level(2).parentId(california.getId()).name("Orange County").build());
        AdministrativeUnit nyCounty = unitRepository.save(AdministrativeUnit.builder().countryId("US").level(2).parentId(newYork.getId()).name("New York County").build());

        // Level 3: City
        unitRepository.save(AdministrativeUnit.builder().countryId("US").level(3).parentId(laCounty.getId()).name("Los Angeles").build());
        unitRepository.save(AdministrativeUnit.builder().countryId("US").level(3).parentId(laCounty.getId()).name("Pasadena").build());
        unitRepository.save(AdministrativeUnit.builder().countryId("US").level(3).parentId(orangeCounty.getId()).name("Anaheim").build());
        unitRepository.save(AdministrativeUnit.builder().countryId("US").level(3).parentId(nyCounty.getId()).name("New York City").build());
    }
}
