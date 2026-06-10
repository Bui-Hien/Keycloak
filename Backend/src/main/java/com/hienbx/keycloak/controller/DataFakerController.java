package com.hienbx.keycloak.controller;

import com.hienbx.keycloak.service.DataFakerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users/fake-data")
@RequiredArgsConstructor
public class DataFakerController {

    private final DataFakerService dataFakerService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> generateFakeData() {
        dataFakerService.runFakeDataGenerationAsync();
        return ResponseEntity.ok(Map.of("message", "Asynchronous generation of 500,000 users has started."));
    }
}
