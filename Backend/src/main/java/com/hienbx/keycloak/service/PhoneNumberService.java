package com.hienbx.keycloak.service;

import com.hienbx.keycloak.entity.LocalUser;
import com.hienbx.keycloak.entity.PhoneNumber;
import com.hienbx.keycloak.repository.UserRepository;
import com.hienbx.keycloak.repository.PhoneNumberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneNumberService {

    private final PhoneNumberRepository phoneNumberRepository;
    private final UserRepository userRepository;

    @Value("${app.phone.max-limit:4}")
    private int maxLimit;

    /**
     * Get paginated list of phone numbers, optionally filtered by number segment.
     */
    public Page<PhoneNumber> listPhoneNumbers(String phoneNumberFilter, int pageIndex, int pageSize) {
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by("id").ascending());
        if (phoneNumberFilter != null && !phoneNumberFilter.trim().isEmpty()) {
            return phoneNumberRepository.searchByNumber(phoneNumberFilter.trim(), pageable);
        } else {
            return phoneNumberRepository.findByType("phone", pageable);
        }
    }

    /**
     * Add a new phone number to a user and generate search suffixes.
     */
    @Transactional
    public PhoneNumber addPhoneNumber(Long userId, String number) {
        LocalUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        // Check maximum limit of phone numbers
        long currentCount = phoneNumberRepository.countByUserAndType(user, "phone");
        if (currentCount >= maxLimit) {
            throw new IllegalArgumentException("User has reached the maximum limit of phone numbers: " + maxLimit);
        }

        String cleanedNumber = number.trim();
        // Save parent phone number
        PhoneNumber parentPhone = PhoneNumber.builder()
                .number(cleanedNumber)
                .type("phone")
                .user(user)
                .build();
        parentPhone = phoneNumberRepository.save(parentPhone);

        // Generate search suffixes and save
        List<String> suffixes = generateSearchSuffixes(cleanedNumber);
        List<PhoneNumber> searchList = new ArrayList<>();
        for (String suffix : suffixes) {
            searchList.add(PhoneNumber.builder()
                    .number(suffix)
                    .type("search")
                    .parentPhone(parentPhone)
                    .user(user)
                    .build());
        }
        if (!searchList.isEmpty()) {
            phoneNumberRepository.saveAll(searchList);
        }

        log.info("Successfully added phone number: {} for user: {} and generated {} search suffixes", cleanedNumber,
                userId, searchList.size());
        return parentPhone;
    }

    /**
     * Update an existing phone number and regenerate search suffixes.
     */
    @Transactional
    public PhoneNumber updatePhoneNumber(Long userId, Long phoneId, String newNumber) {
        LocalUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        PhoneNumber parentPhone = phoneNumberRepository.findById(phoneId)
                .orElseThrow(() -> new IllegalArgumentException("Phone number not found with ID: " + phoneId));

        if (!parentPhone.getUser().getId().equals(userId) || !"phone".equals(parentPhone.getType())) {
            throw new IllegalArgumentException("Invalid phone number or it does not belong to the specified user.");
        }

        // Delete old search suffixes
        phoneNumberRepository.deleteByParentPhone(parentPhone);

        String cleanedNumber = newNumber.trim();
        // Update parent phone number
        parentPhone.setNumber(cleanedNumber);
        parentPhone = phoneNumberRepository.save(parentPhone);

        // Generate and save new search suffixes
        List<String> suffixes = generateSearchSuffixes(cleanedNumber);
        List<PhoneNumber> searchList = new ArrayList<>();
        for (String suffix : suffixes) {
            searchList.add(PhoneNumber.builder()
                    .number(suffix)
                    .type("search")
                    .parentPhone(parentPhone)
                    .user(user)
                    .build());
        }
        if (!searchList.isEmpty()) {
            phoneNumberRepository.saveAll(searchList);
        }

        log.info("Successfully updated phone number ID: {} to {} for user: {}", phoneId, cleanedNumber, userId);
        return parentPhone;
    }

    /**
     * Delete an existing phone number and all its search suffixes.
     */
    @Transactional
    public void deletePhoneNumber(Long userId, Long phoneId) {
        PhoneNumber parentPhone = phoneNumberRepository.findById(phoneId)
                .orElseThrow(() -> new IllegalArgumentException("Phone number not found with ID: " + phoneId));

        if (!parentPhone.getUser().getId().equals(userId) || !"phone".equals(parentPhone.getType())) {
            throw new IllegalArgumentException("Invalid phone number or it does not belong to the specified user.");
        }

        // Delete all search suffixes
        phoneNumberRepository.deleteByParentPhone(parentPhone);

        // Delete the original phone number
        phoneNumberRepository.delete(parentPhone);

        log.info("Successfully deleted phone number ID: {} for user: {}", phoneId, userId);
    }

    /**
     * Helper to generate search suffixes based on user requirement:
     * If input is "0763433779" -> returns: 0763433779, 763433779, 3433779, 433779,
     * 33779, 3779, 779, 79, 9
     */
    public List<String> generateSearchSuffixes(String number) {
        List<String> suffixes = new ArrayList<>();
        if (number == null || number.length() < 4) {
            return suffixes;
        }

        // Add the original number itself
        suffixes.add(number);

        if (number.startsWith("0")) {
            // Suffix without leading '0'
            suffixes.add(number.substring(1));
            // Suffixes starting from the subscriber body (index 3 onwards)
            for (int i = 3; i < number.length(); i++) {
                suffixes.add(number.substring(i));
            }
        } else {
            // For numbers not starting with 0, generate all suffixes from index 1 to length
            // - 1
            for (int i = 1; i < number.length(); i++) {
                suffixes.add(number.substring(i));
            }
        }
        return suffixes;
    }
}
