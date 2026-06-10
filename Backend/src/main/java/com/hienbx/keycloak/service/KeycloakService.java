package com.hienbx.keycloak.service;

import com.hienbx.keycloak.dto.UserDto;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.hienbx.keycloak.repository.UserRepository;
import com.hienbx.keycloak.entity.PhoneNumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakService {

    private final Keycloak keycloak;
    private final UserRepository userRepository;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private RealmResource getRealm() {
        return keycloak.realm(realm);
    }

    private UsersResource getUsersResource() {
        return getRealm().users();
    }

    // Get all users
    public List<UserDto> getAllUsers() {
        List<UserRepresentation> users = getUsersResource().list();
        return users.stream().map(this::toDto).collect(Collectors.toList());
    }

    // Get users with pagination
    public List<UserDto> getUsersPaginated(int page, int size) {
        int firstResult = page * size;
        List<UserRepresentation> users = getUsersResource().list(firstResult, size);
        return users.stream().map(this::toDto).collect(Collectors.toList());
    }

    // Get user by ID
    public UserDto getUserById(String id) {
        UserRepresentation user = getUsersResource().get(id).toRepresentation();
        return toDto(user);
    }

    // Get user by ID or Username
    public UserDto getUserByIdOrUsername(String identifier) {
        UserRepresentation user = null;
        try {
            // First try fetching by ID
            user = getUsersResource().get(identifier).toRepresentation();
        } catch (Exception e) {
            // If ID fetch fails, search by exact username match
            List<UserRepresentation> found = getUsersResource().searchByUsername(identifier, true);
            if (found != null && !found.isEmpty()) {
                user = found.get(0);
            }
        }
        if (user == null) {
            throw new RuntimeException("User not found with ID or Username: " + identifier);
        }
        return toDto(user);
    }

    // Change user password with old password verification
    public void changePassword(String idOrUsername, String oldPassword, String newPassword) {
        UserDto userDto = getUserByIdOrUsername(idOrUsername);
        
        // Verify the old password first
        if (!verifyOldPassword(userDto.getUsername(), oldPassword)) {
            throw new IllegalArgumentException("Incorrect old password");
        }
        
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setTemporary(false);
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        getUsersResource().get(userDto.getId()).resetPassword(credential);
    }

    // Helper to verify old password by testing token authentication
    public boolean verifyOldPassword(String username, String oldPassword) {
        try (Keycloak userKeycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .username(username)
                .password(oldPassword)
                .grantType(OAuth2Constants.PASSWORD)
                .build()) {
            
            // Trigger token acquisition to verify credentials
            userKeycloak.tokenManager().getAccessToken();
            return true;
        } catch (Exception e) {
            log.warn("Password verification failed for user '{}': {}", username, e.getMessage());
            return false;
        }
    }

    // Check if username already exists in Keycloak
    public boolean existsByUsername(String username) {
        List<UserRepresentation> found = getUsersResource().searchByUsername(username, true);
        return found != null && !found.isEmpty();
    }

    // Create user
    public UserDto createUser(UserDto userDto) {
        if (userDto.getUsername() == null || userDto.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (existsByUsername(userDto.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + userDto.getUsername());
        }

        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setEmailVerified(true);

        // Configure credentials
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setTemporary(false);
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(userDto.getPassword());
        user.setCredentials(Collections.singletonList(credential));

        Response response = getUsersResource().create(user);
        if (response.getStatus() != 201) {
            log.error("Failed to create user. Status: {}", response.getStatus());
            throw new RuntimeException("Failed to create user in Keycloak. Status code: " + response.getStatus());
        }

        // Get created user ID from response header "Location"
        String path = response.getLocation().getPath();
        String createdUserId = path.substring(path.lastIndexOf('/') + 1);

        // Assign roles if specified
        if (userDto.getRoles() != null && !userDto.getRoles().isEmpty()) {
            assignRolesToUser(createdUserId, userDto.getRoles());
        }

        return getUserById(createdUserId);
    }

    // Update user
    public UserDto updateUser(String id, UserDto userDto) {
        UserResource userResource = getUsersResource().get(id);
        UserRepresentation user = userResource.toRepresentation();

        // Check if username is being changed, and verify its uniqueness
        if (userDto.getUsername() != null && !userDto.getUsername().trim().isEmpty() &&
            !userDto.getUsername().equals(user.getUsername())) {
            if (existsByUsername(userDto.getUsername())) {
                throw new IllegalArgumentException("Username already exists: " + userDto.getUsername());
            }
            user.setUsername(userDto.getUsername());
        }

        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setEmail(userDto.getEmail());

        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setTemporary(false);
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(userDto.getPassword());
            userResource.resetPassword(credential);
        }

        userResource.update(user);

        // Update roles if specified
        if (userDto.getRoles() != null) {
            // Remove all existing realm roles
            List<RoleRepresentation> existingRoles = userResource.roles().realmLevel().listAll();
            userResource.roles().realmLevel().remove(existingRoles);
            // Assign new roles
            assignRolesToUser(id, userDto.getRoles());
        }

        return getUserById(id);
    }

    // Delete user
    public void deleteUser(String id) {
        Response response = getUsersResource().delete(id);
        if (response.getStatus() != 204 && response.getStatus() != 200) {
            log.error("Failed to delete user {}. Status: {}", id, response.getStatus());
            throw new RuntimeException("Failed to delete user in Keycloak");
        }
    }

    private void assignRolesToUser(String userId, List<String> roleNames) {
        List<RoleRepresentation> rolesToAssign = new ArrayList<>();
        for (String roleName : roleNames) {
            try {
                RoleRepresentation role = getRealm().roles().get(roleName).toRepresentation();
                rolesToAssign.add(role);
            } catch (Exception e) {
                log.warn("Role {} does not exist in Keycloak, skipping assignment.", roleName);
            }
        }
        if (!rolesToAssign.isEmpty()) {
            getUsersResource().get(userId).roles().realmLevel().add(rolesToAssign);
        }
    }

    private UserDto toDto(UserRepresentation user) {
        List<RoleRepresentation> roleRepresentations = getUsersResource().get(user.getId()).roles().realmLevel()
                .listAll();
        List<String> roles = roleRepresentations.stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toList());

        List<String> phoneNumbers = userRepository.findByKeycloakId(user.getId())
                .map(localUser -> localUser.getPhoneNumbers().stream()
                        .map(PhoneNumber::getNumber)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());

        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(roles)
                .phoneNumbers(phoneNumbers)
                .build();
    }
}
