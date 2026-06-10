package com.hienbx.keycloak.controller;

import com.hienbx.keycloak.dto.UserDto;
import com.hienbx.keycloak.service.KeycloakService;
import com.hienbx.keycloak.service.LocalUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Slice;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final KeycloakService keycloakService;
    private final LocalUserService localUserService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<Slice<UserDto>> getAllUsers(
            @RequestParam(name = "pageIndex", defaultValue = "0") int pageIndex,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(name = "phoneNumberIndex", required = false) String phoneNumberIndex,
            @RequestParam(name = "phoneNumber", required = false) String phoneNumber) {
        return ResponseEntity.ok(localUserService.getAllUsers(pageIndex, pageSize, phoneNumberIndex, phoneNumber));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<UserDto> getUserById(@PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(localUserService.getUserById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> createUser(@RequestBody UserDto userDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(localUserService.createUser(userDto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> updateUser(@PathVariable(name = "id") Long id, @RequestBody UserDto userDto) {
        return ResponseEntity.ok(localUserService.updateUser(id, userDto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable(name = "id") Long id) {
        localUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{idOrUsername}/change-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changePassword(@PathVariable String idOrUsername,
            @RequestBody Map<String, String> payload) {
        String oldPassword = payload.get("oldPassword");
        if (oldPassword == null) {
            oldPassword = payload.get("old_password");
        }

        String newPassword = payload.get("newPassword");
        if (newPassword == null) {
            newPassword = payload.get("password");
        }
        if (newPassword == null) {
            newPassword = payload.get("new_password");
        }

        if (oldPassword == null || oldPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Old password cannot be empty");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("New password cannot be empty");
        }

        keycloakService.changePassword(idOrUsername, oldPassword, newPassword);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/exists/{username}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<Boolean> existsByUsername(@PathVariable String username) {
        return ResponseEntity.ok(keycloakService.existsByUsername(username));
    }

    @PostMapping("/sync-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> syncAllUsers() {
        localUserService.syncAllUsers();
        return ResponseEntity.ok(Map.of("message", "Batch synchronization completed successfully"));
    }
}
