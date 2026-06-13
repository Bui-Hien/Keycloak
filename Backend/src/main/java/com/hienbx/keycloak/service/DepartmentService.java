package com.hienbx.keycloak.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.hienbx.keycloak.dto.DepartmentDto;
import com.hienbx.keycloak.entity.Department;
import com.hienbx.keycloak.repository.DepartmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentService {
    private final DepartmentRepository departmentRepository;

    @Transactional
    public DepartmentDto createOrUpdateDepartment(DepartmentDto dto) {
        if (dto == null) {
            return null;
        }
        Department entity = null;
        if (dto.getId() != null) {
            entity = departmentRepository.findById(dto.getId()).orElse(null);
        }
        if (entity == null) {
            entity = new Department();
            entity.setMpath("");
        }
        entity.setName(dto.getName());
        entity.setCode(dto.getCode());
        entity.setDescription(dto.getDescription());

        Department parent = null;
        if (dto.getParentId() != null) {
            parent = departmentRepository.findById(dto.getParentId()).orElse(null);
        }
        entity.setParent(parent);

        if (entity.getId() != null) {
            if (parent == null) {
                entity.setMpath("/" + entity.getId() + "/");
            } else {
                entity.setMpath(parent.getMpath() + entity.getId() + "/");
            }
            entity = departmentRepository.save(entity);
        } else {
            entity = departmentRepository.save(entity);
            if (parent == null) {
                entity.setMpath("/" + entity.getId() + "/");
            } else {
                entity.setMpath(parent.getMpath() + entity.getId() + "/");
            }
            entity = departmentRepository.save(entity);
        }

        return new DepartmentDto(entity);
    }

    public boolean deleteDepartmentById(Long id) {
        if (id == null) {
            return false;
        }

        Department entity = departmentRepository.findById(id).orElse(null);
        if (entity == null) {
            return false;
        }
        departmentRepository.delete(entity);

        return true;
    }

    public Page<DepartmentDto> pagingDepartment(Pageable pageable, Long parentId, String keyword) {
        if (pageable == null) {
            return null;
        }
        if (!StringUtils.hasText(keyword)) {
            return this.pagingDepartment(pageable, parentId);
        } else {
            return this.pagingDepartmentByKeyword(pageable, keyword);
        }
    }

    private Page<DepartmentDto> pagingDepartment(Pageable pageable, Long parentId) {
        return departmentRepository.findByPage(pageable, parentId);
    }

    private Page<DepartmentDto> pagingDepartmentByKeyword(Pageable pageable, String keyword) {
        Page<DepartmentDto> data = departmentRepository.findByPage(pageable, keyword);

        return buildTreeView(data);
    }

    private Page<DepartmentDto> buildTreeView(Page<DepartmentDto> data) {
        if (data == null || data.getContent().isEmpty()) {
            return null;
        }
        List<DepartmentDto> listDepartment = data.getContent();

        Set<Long> ids = new HashSet<>();

        for (DepartmentDto dto : listDepartment) {
            if (dto == null || !StringUtils.hasText(dto.getMpath())) {
                continue;
            }
            String[] pathParts = dto.getMpath().split("/");
            for (String part : pathParts) {
                if (StringUtils.hasText(part)) {
                    ids.add(Long.valueOf(part));
                }
            }
        }

        if (ids.isEmpty()) {
            return null;
        }

        List<DepartmentDto> parentList = departmentRepository.findByIds(ids.stream().toList());
        if (parentList == null || parentList.isEmpty()) {
            return null;
        }

        Map<Long, DepartmentDto> map = parentList.stream()
                .collect(Collectors.toMap(DepartmentDto::getId, dto -> dto, (existing, replacement) -> existing));

        List<DepartmentDto> roots = new ArrayList<>();

        for (DepartmentDto dto : parentList) {
            if (dto.getChildren() == null) {
                dto.setChildren(new ArrayList<>());
            }
            Long parentId = dto.getParentId();
            if (parentId != null && map.containsKey(parentId)) {
                DepartmentDto parentDto = map.get(parentId);
                if (parentDto.getChildren() == null) {
                    parentDto.setChildren(new ArrayList<>());
                }
                if (!parentDto.getChildren().contains(dto)) {
                    parentDto.getChildren().add(dto);
                }
            } else {
                roots.add(dto);
            }
        }

        return new PageImpl<>(roots, data.getPageable(), data.getTotalElements());
    }

    @Transactional
    public void generateFakeDepartments() {
        List<Long> createdIds = new ArrayList<>();
        
        // 1. Create exactly 100 root departments (parentId = null)
        for (int i = 1; i <= 100; i++) {
            DepartmentDto dto = DepartmentDto.builder()
                    .name("Phòng ban gốc " + i)
                    .code("PB_ROOT_" + String.format("%03d", i))
                    .description("Mô tả phòng ban gốc " + i)
                    .parentId(null)
                    .build();
            DepartmentDto saved = createOrUpdateDepartment(dto);
            if (saved != null && saved.getId() != null) {
                createdIds.add(saved.getId());
            }
        }

        // 2. Create a deep chain of at least 5 levels from the first root node
        if (!createdIds.isEmpty()) {
            Long currentParentId = createdIds.get(0);
            for (int level = 2; level <= 6; level++) { // Creates nested children from Level 2 down to Level 6
                DepartmentDto dto = DepartmentDto.builder()
                        .name("Phòng ban con cấp " + level)
                        .code("PB_CHAIN_" + level)
                        .description("Mô tả phòng ban cấp " + level)
                        .parentId(currentParentId)
                        .build();
                DepartmentDto saved = createOrUpdateDepartment(dto);
                if (saved != null && saved.getId() != null) {
                    createdIds.add(saved.getId());
                    currentParentId = saved.getId(); // Gán làm cha cho cấp tiếp theo
                }
            }
        }

        // 3. Create the remaining departments (up to 1000 nodes total)
        // All these remaining departments must have a valid parent, keeping the root node count strictly at 100.
        int totalNodes = 1000;
        int currentCount = createdIds.size();
        for (int i = currentCount + 1; i <= totalNodes; i++) {
            Long parentId = createdIds.get((int) (Math.random() * createdIds.size()));

            DepartmentDto dto = DepartmentDto.builder()
                    .name("Phòng ban " + i)
                    .code("PB_" + String.format("%04d", i))
                    .description("Mô tả phòng ban " + i)
                    .parentId(parentId)
                    .build();

            DepartmentDto saved = createOrUpdateDepartment(dto);
            if (saved != null && saved.getId() != null) {
                createdIds.add(saved.getId());
            }
        }
    }
}

