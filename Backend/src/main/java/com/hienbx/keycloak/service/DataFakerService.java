package com.hienbx.keycloak.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataFakerService {

    private final DataSource dataSource;
    private final PhoneNumberService phoneNumberService;

    /**
     * Start async generation of 500,000 users using 5 threads.
     */
    public void runFakeDataGenerationAsync() {
        CompletableFuture.runAsync(() -> {
            log.info("Starting async generation of 500,000 users...");
            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int t = 0; t < 5; t++) {
                final int threadId = t;
                futures.add(CompletableFuture.runAsync(() -> {
                    generateForThread(threadId);
                }, executor));
            }

            // Wait for all threads to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();
            log.info("Finished async generation of 500,000 users successfully!");
        }).exceptionally(ex -> {
            log.error("Error during async fake data generation", ex);
            return null;
        });
    }

    private void generateForThread(int threadId) {
        log.info("Thread-{}: Starting generation of 100,000 users...", threadId);
        int totalUsers = 100000;
        int batchSize = 1000;
        int numBatches = totalUsers / batchSize;

        for (int b = 0; b < numBatches; b++) {
            long startTime = System.currentTimeMillis();
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                String insertUserSql = "INSERT INTO local_user (keycloak_id, username, email, first_name, last_name, created_at, updated_at, phone_number_1, phone_number_2, phone_number_3, phone_number_4) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                String insertPhoneSql = "INSERT INTO phone_number (number, type, phone_id, user_id) VALUES (?, ?, ?, ?)";

                try (PreparedStatement userStmt = conn.prepareStatement(insertUserSql, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement phoneStmt = conn.prepareStatement(insertPhoneSql, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement searchStmt = conn.prepareStatement(insertPhoneSql)) {

                    int startIdx = threadId * totalUsers + b * batchSize;

                    for (int i = 0; i < batchSize; i++) {
                        int currentIdx = startIdx + i;
                        String uuid = UUID.randomUUID().toString();
                        String username = "faker_" + currentIdx;
                        String email = username + "@hiendev.online";
                        String firstName = "FakerFirst" + currentIdx;
                        String lastName = "FakerLast" + currentIdx;
                        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

                        // Generate 4 phone numbers for this user first
                        String[] phones = new String[4];
                        for (int p = 0; p < 4; p++) {
                            long numberVal = 900000000L + ((long) currentIdx * 4) + p;
                            phones[p] = "0" + numberVal;
                        }

                        // Set User fields
                        userStmt.setString(1, uuid);
                        userStmt.setString(2, username);
                        userStmt.setString(3, email);
                        userStmt.setString(4, firstName);
                        userStmt.setString(5, lastName);
                        userStmt.setTimestamp(6, now);
                        userStmt.setTimestamp(7, now);
                        userStmt.setString(8, phones[0]);
                        userStmt.setString(9, phones[1]);
                        userStmt.setString(10, phones[2]);
                        userStmt.setString(11, phones[3]);

                        userStmt.executeUpdate();

                        // Get generated user ID
                        long userId;
                        try (ResultSet rs = userStmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                userId = rs.getLong(1);
                            } else {
                                throw new SQLException("Creating user failed, no ID obtained.");
                            }
                        }

                        // Save to phone_number table
                        for (int p = 0; p < 4; p++) {
                            String numberStr = phones[p];

                            phoneStmt.setString(1, numberStr);
                            phoneStmt.setString(2, "phone");
                            phoneStmt.setNull(3, Types.BIGINT);
                            phoneStmt.setLong(4, userId);

                            phoneStmt.executeUpdate();

                            // Get generated phone ID
                            long phoneId;
                            try (ResultSet rs = phoneStmt.getGeneratedKeys()) {
                                if (rs.next()) {
                                    phoneId = rs.getLong(1);
                                } else {
                                    throw new SQLException("Creating phone failed, no ID obtained.");
                                }
                            }

                            // Generate search suffixes for this phone number and batch insert them
                            List<String> suffixes = phoneNumberService.generateSearchSuffixes(numberStr);
                            for (String suffix : suffixes) {
                                searchStmt.setString(1, suffix);
                                searchStmt.setString(2, "search");
                                searchStmt.setLong(3, phoneId);
                                searchStmt.setLong(4, userId);
                                searchStmt.addBatch();
                            }
                        }
                    }

                    // Execute batch insert for search suffixes
                    searchStmt.executeBatch();

                    conn.commit();
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (Exception e) {
                log.error("Thread-{}: Error at batch {}", threadId, b, e);
                return;
            }
            long duration = System.currentTimeMillis() - startTime;
            if (b % 10 == 0 || b == numBatches - 1) {
                log.info("Thread-{}: Completed batch {}/{} ({} users) in {}ms", threadId, b + 1, numBatches, (b + 1) * batchSize, duration);
            }
        }
        log.info("Thread-{}: Completed generation of 100,000 users successfully!", threadId);
    }
}
