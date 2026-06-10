package com.hienbx.keycloak.repository;

import com.hienbx.keycloak.entity.LocalUser;
import com.hienbx.keycloak.entity.PhoneNumber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PhoneNumberRepository extends JpaRepository<PhoneNumber, Long> {

    Page<PhoneNumber> findByType(String type, Pageable pageable);

    @Query("SELECT DISTINCT COALESCE(p.parentPhone, p) FROM PhoneNumber p WHERE p.number = :number")
    Page<PhoneNumber> searchByNumber(@Param("number") String number, Pageable pageable);

    long countByUserAndType(LocalUser user, String type);

    void deleteByParentPhone(PhoneNumber parentPhone);
}
