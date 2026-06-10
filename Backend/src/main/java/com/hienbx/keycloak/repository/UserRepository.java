package com.hienbx.keycloak.repository;

import com.hienbx.keycloak.entity.LocalUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<LocalUser, Long> {

    Optional<LocalUser> findByKeycloakId(String keycloakId);

    Optional<LocalUser> findByUsername(String username);

    @Query("SELECT u FROM LocalUser u, PhoneNumber p WHERE p.user = u AND p.number = :phoneNumberIndex GROUP BY u.id")
    Slice<LocalUser> findByPhoneNumberIndex(@Param("phoneNumberIndex") String phoneNumberIndex, Pageable pageable);

    @Query("SELECT u FROM LocalUser u WHERE u.phoneNumber1 LIKE CONCAT('%', :phoneNumber, '%') OR u.phoneNumber2 LIKE CONCAT('%', :phoneNumber, '%') OR u.phoneNumber3 LIKE CONCAT('%', :phoneNumber, '%') OR u.phoneNumber4 LIKE CONCAT('%', :phoneNumber, '%')")
    Slice<LocalUser> findByPhoneNumberLike(@Param("phoneNumber") String phoneNumber, Pageable pageable);

    @Query("SELECT u FROM LocalUser u, PhoneNumber p WHERE p.user = u AND p.number = :phoneNumberIndex AND (u.phoneNumber1 LIKE CONCAT('%', :phoneNumber, '%') OR u.phoneNumber2 LIKE CONCAT('%', :phoneNumber, '%') OR u.phoneNumber3 LIKE CONCAT('%', :phoneNumber, '%') OR u.phoneNumber4 LIKE CONCAT('%', :phoneNumber, '%')) GROUP BY u.id")
    Slice<LocalUser> searchByBoth(@Param("phoneNumberIndex") String phoneNumberIndex, @Param("phoneNumber") String phoneNumber, Pageable pageable);

    @Query("SELECT u FROM LocalUser u")
    Slice<LocalUser> findAllUsers(Pageable pageable);
}
