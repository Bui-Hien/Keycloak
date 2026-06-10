package com.hienbx.keycloak.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "local_user", indexes = {
        @Index(name = "idx_keycloak_id", columnList = "keycloak_id", unique = true),
        @Index(name = "idx_username", columnList = "username", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_id", nullable = false, length = 36)
    private String keycloakId;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(length = 100)
    private String email;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "phone_number_1", length = 20)
    private String phoneNumber1;

    @Column(name = "phone_number_2", length = 20)
    private String phoneNumber2;

    @Column(name = "phone_number_3", length = 20)
    private String phoneNumber3;

    @Column(name = "phone_number_4", length = 20)
    private String phoneNumber4;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @SQLRestriction("type = 'phone'")
    @OrderBy("id ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<PhoneNumber> phoneNumbers = new ArrayList<>();
}
