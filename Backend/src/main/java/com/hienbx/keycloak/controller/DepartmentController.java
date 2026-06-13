package com.hienbx.keycloak.controller;

import com.hienbx.keycloak.dto.DepartmentDto;
import com.hienbx.keycloak.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentDto> createOrUpdateDepartment(@RequestBody DepartmentDto dto) {
        return ResponseEntity.ok(departmentService.createOrUpdateDepartment(dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Boolean> deleteDepartmentById(@PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(departmentService.deleteDepartmentById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public ResponseEntity<Page<DepartmentDto>> pagingDepartment(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "sort", defaultValue = "id,asc") String sort,
            @RequestParam(name = "parentId", required = false) Long parentId,
            @RequestParam(name = "keyword", required = false) String keyword) {

        Sort sortObj = Sort.unsorted();
        if (sort != null && sort.contains(",")) {
            String[] sortParts = sort.split(",");
            if (sortParts.length == 2) {
                sortObj = Sort.by(
                        sortParts[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC,
                        sortParts[0]);
            }
        }
        Pageable pageable = PageRequest.of(page, size, sortObj);
        return ResponseEntity.ok(departmentService.pagingDepartment(pageable, parentId, keyword));
    }

    @PostMapping("/fake-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> generateFakeDepartments() {
        departmentService.generateFakeDepartments();
        return ResponseEntity.ok(Map.of("message", "Generated 10000 hierarchical fake departments successfully."));
    }
}
