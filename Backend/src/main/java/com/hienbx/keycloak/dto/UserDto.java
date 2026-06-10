package com.hienbx.keycloak.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private List<String> roles;
    private List<String> phoneNumbers;
    private String phoneNumber1;
    private String phoneNumber2;
    private String phoneNumber3;
    private String phoneNumber4;
}
