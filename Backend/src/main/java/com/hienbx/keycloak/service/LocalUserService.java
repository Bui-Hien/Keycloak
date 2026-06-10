package com.hienbx.keycloak.service;

import com.hienbx.keycloak.dto.UserDto;
import com.hienbx.keycloak.entity.LocalUser;
import com.hienbx.keycloak.entity.PhoneNumber;
import com.hienbx.keycloak.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalUserService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final PlatformTransactionManager transactionManager;
    private final PhoneNumberService phoneNumberService;

    @Value("${app.phone.max-limit:5}")
    private int maxLimit;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void syncUser(Jwt jwt) {
        String keycloakId = jwt.getSubject(); // 'sub' claim
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");

        Optional<LocalUser> existingUser = userRepository.findByKeycloakId(keycloakId);
        if (existingUser.isPresent()) {
            LocalUser user = existingUser.get();
            // Check if any info has changed and update it
            boolean updated = false;
            if (username != null && !username.equals(user.getUsername())) {
                user.setUsername(username);
                updated = true;
            }
            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
                updated = true;
            }
            if (firstName != null && !firstName.equals(user.getFirstName())) {
                user.setFirstName(firstName);
                updated = true;
            }
            if (lastName != null && !lastName.equals(user.getLastName())) {
                user.setLastName(lastName);
                updated = true;
            }
            if (updated) {
                user.setUpdatedAt(LocalDateTime.now());
                userRepository.save(user);
                log.info("Synchronized updated user profile for username: {}", username);
            }
        } else {
            // Create new local user
            LocalUser newUser = LocalUser.builder()
                    .keycloakId(keycloakId)
                    .username(username != null ? username : "user_" + keycloakId.substring(0, 8))
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(newUser);
            log.info("Created new local user in DB during login sync for username: {}", username);
        }
    }

    // Synchronize all user accounts from Keycloak to local MySQL database
    // (page-by-page)
    // No class-level or method-level transaction to allow independent page
    // transactions
    public void syncAllUsers() {
        log.info("Starting batch page-by-page synchronization of all users from Keycloak to local DB");

        int page = 0;
        int pageSize = 100;
        int addedCount = 0;
        int updatedCount = 0;

        List<UserDto> keycloakUsers;

        // TransactionTemplate to run each page in its own transaction
        // (PROPAGATION_REQUIRES_NEW)
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        do {
            keycloakUsers = keycloakService.getUsersPaginated(page, pageSize);
            log.info("Fetched page {} with {} users from Keycloak", page, keycloakUsers.size());

            final List<UserDto> usersToProcess = keycloakUsers;
            final int currentPage = page;

            try {
                int[] stats = transactionTemplate.execute(status -> {
                    int added = 0;
                    int updated = 0;
                    List<LocalUser> localUserList = new ArrayList<>();

                    for (UserDto ku : usersToProcess) {
                        Optional<LocalUser> existingUser = userRepository.findByKeycloakId(ku.getId());
                        if (existingUser.isPresent()) {
                            LocalUser user = existingUser.get();
                            boolean isUpdated = false;

                            if (ku.getUsername() != null && !ku.getUsername().equals(user.getUsername())) {
                                user.setUsername(ku.getUsername());
                                isUpdated = true;
                            }
                            if (ku.getEmail() != null && !ku.getEmail().equals(user.getEmail())) {
                                user.setEmail(ku.getEmail());
                                isUpdated = true;
                            }
                            if (ku.getFirstName() != null && !ku.getFirstName().equals(user.getFirstName())) {
                                user.setFirstName(ku.getFirstName());
                                isUpdated = true;
                            }
                            if (ku.getLastName() != null && !ku.getLastName().equals(user.getLastName())) {
                                user.setLastName(ku.getLastName());
                                isUpdated = true;
                            }

                            if (isUpdated) {
                                user.setUpdatedAt(LocalDateTime.now());
                                localUserList.add(user);
                                updated++;
                            }
                        } else {
                            LocalUser newUser = LocalUser.builder()
                                    .keycloakId(ku.getId())
                                    .username(ku.getUsername() != null ? ku.getUsername()
                                            : "user_" + ku.getId().substring(0, 8))
                                    .email(ku.getEmail())
                                    .firstName(ku.getFirstName())
                                    .lastName(ku.getLastName())
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            localUserList.add(newUser);
                            added++;
                        }
                    }

                    if (!localUserList.isEmpty()) {
                        userRepository.saveAll(localUserList);
                        localUserList.clear();
                    }

                    // Flush and clear inside the transaction boundary
                    entityManager.flush();
                    entityManager.clear();

                    return new int[] { added, updated };
                });

                if (stats != null) {
                    addedCount += stats[0];
                    updatedCount += stats[1];
                }
                log.info("Successfully synchronized page {}", currentPage);
            } catch (Exception e) {
                log.error("Failed to synchronize page {} due to error: {}. Continuing with next page...", currentPage,
                        e.getMessage(), e);
            }

            page++;
        } while (keycloakUsers.size() == pageSize);

        log.info("Batch synchronization completed successfully. Total processed pages: {}, Added: {}, Updated: {}",
                page, addedCount, updatedCount);
    }

    // Mapping helper to convert LocalUser to UserDto
    public UserDto toDto(LocalUser user) {
        if (user == null) {
            return null;
        }
        List<String> phoneNumbers = user.getPhoneNumbers().stream()
                .filter(p -> "phone".equals(p.getType()))
                .map(PhoneNumber::getNumber)
                .collect(Collectors.toList());

        return UserDto.builder()
                .id(String.valueOf(user.getId()))
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumbers(phoneNumbers)
                .phoneNumber1(user.getPhoneNumber1())
                .phoneNumber2(user.getPhoneNumber2())
                .phoneNumber3(user.getPhoneNumber3())
                .phoneNumber4(user.getPhoneNumber4())
                .build();
    }

    // Sync user's phone numbers (original + search suffixes)
    private void syncUserPhoneNumbers(LocalUser user, List<String> newPhones) {
        user.getPhoneNumbers().clear();
        user.setPhoneNumber1(null);
        user.setPhoneNumber2(null);
        user.setPhoneNumber3(null);
        user.setPhoneNumber4(null);

        if (newPhones != null) {
            List<String> limitedPhones = newPhones;
            if (newPhones.size() > maxLimit) {
                limitedPhones = newPhones.subList(0, maxLimit);
            }

            for (int i = 0; i < limitedPhones.size(); i++) {
                String cleaned = limitedPhones.get(i).trim();
                if (i == 0)
                    user.setPhoneNumber1(cleaned);
                else if (i == 1)
                    user.setPhoneNumber2(cleaned);
                else if (i == 2)
                    user.setPhoneNumber3(cleaned);
                else if (i == 3)
                    user.setPhoneNumber4(cleaned);
            }

            for (String rawPhone : limitedPhones) {
                String cleaned = rawPhone.trim();
                PhoneNumber parentPhone = PhoneNumber.builder()
                        .number(cleaned)
                        .type("phone")
                        .user(user)
                        .build();
                user.getPhoneNumbers().add(parentPhone);

                // Generate and add search suffixes
                List<String> suffixes = phoneNumberService.generateSearchSuffixes(cleaned);
                for (String suffix : suffixes) {
                    PhoneNumber searchPhone = PhoneNumber.builder()
                            .number(suffix)
                            .type("search")
                            .parentPhone(parentPhone)
                            .user(user)
                            .build();
                    user.getPhoneNumbers().add(searchPhone);
                }
            }
        }
    }

    // CRUD: List users with pagination and phone number filter
    public Slice<UserDto> getAllUsers(int pageIndex, int pageSize, String phoneNumberIndex, String phoneNumber) {
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by("id").ascending());
        Slice<LocalUser> userSlice;
        
        String activeQuery = "";
        boolean isIndexedSearch = true;

        if (phoneNumberIndex != null && !phoneNumberIndex.trim().isEmpty()) {
            activeQuery = phoneNumberIndex.trim();
            isIndexedSearch = true;
        } else if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            activeQuery = phoneNumber.trim();
            isIndexedSearch = false;
        }

        if (activeQuery.isEmpty()) {
            userSlice = userRepository.findAllUsers(pageable);
        } else {
            // Dynamic Routing: If query length is less than 4, LIKE search on main table is much faster 
            // because of early exit. If query length is 4 or more, Indexed Search on sub-table is faster.
            if (activeQuery.length() < 4) {
                userSlice = userRepository.findByPhoneNumberLike(activeQuery, pageable);
            } else {
                if (isIndexedSearch) {
                    userSlice = userRepository.findByPhoneNumberIndex(activeQuery, pageable);
                } else {
                    userSlice = userRepository.findByPhoneNumberLike(activeQuery, pageable);
                }
            }
        }
        return userSlice.map(this::toDto);
    }

    // CRUD: Get user by ID
    public UserDto getUserById(Long id) {
        LocalUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
        return toDto(user);
    }

    // CRUD: Create user
    @Transactional
    public UserDto createUser(UserDto userDto) {
        LocalUser user = LocalUser.builder()
                .keycloakId(java.util.UUID.randomUUID().toString()) // Dummy UUID
                .username(userDto.getUsername())
                .email(userDto.getEmail())
                .firstName(userDto.getFirstName())
                .lastName(userDto.getLastName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (userDto.getPhoneNumbers() != null) {
            syncUserPhoneNumbers(user, userDto.getPhoneNumbers());
        }

        user = userRepository.save(user);
        log.info("Created local user ID: {}", user.getId());
        return toDto(user);
    }

    // CRUD: Update user
    @Transactional
    public UserDto updateUser(Long id, UserDto userDto) {
        LocalUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));

        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setUpdatedAt(LocalDateTime.now());

        if (userDto.getPhoneNumbers() != null) {
            syncUserPhoneNumbers(user, userDto.getPhoneNumbers());
        }

        user = userRepository.save(user);
        log.info("Updated local user ID: {}", id);
        return toDto(user);
    }

    // CRUD: Delete user
    @Transactional
    public void deleteUser(Long id) {
        LocalUser user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
        userRepository.delete(user);
        log.info("Deleted local user ID: {}", id);
    }
}
