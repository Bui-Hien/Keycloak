package com.hienbx.keycloak.service;

import com.hienbx.keycloak.dto.DepartmentDto;
import com.hienbx.keycloak.entity.Department;
import com.hienbx.keycloak.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final EntityManager entityManager;

    @Transactional
    public DepartmentDto createOrUpdateDepartment(DepartmentDto dto) {
        if (dto == null) {
            return null;
        }
        Department entity = null;
        if (dto.getId() != null) {
            entity = departmentRepository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Department not found with id: " + dto.getId()));
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
            parent = departmentRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent department not found with id: " + dto.getParentId()));
        }
        entity.setParent(parent);

        if (entity.getId() == null) {
            entity = departmentRepository.save(entity);
        }

        String oldMpath = entity.getMpath();
        String newMpath = (parent == null) ? ("/" + entity.getId() + "/") : (parent.getMpath() + entity.getId() + "/");

        Long oldRootId = entity.getRootId();
        Long newRootId = (parent == null) ? entity.getId() : parent.getRootId();

        boolean isMpathChanged = !newMpath.equals(oldMpath);
        boolean isRootIdChanged = !Objects.equals(newRootId, oldRootId);

        if (isMpathChanged || isRootIdChanged) {
            entity.setMpath(newMpath);
            entity.setRootId(newRootId);
            if (dto.getId() != null && StringUtils.hasText(oldMpath)) {
                departmentRepository.updateMpathAndRootIdPrefix(oldMpath, newMpath, newRootId);
            }
        }

        return new DepartmentDto(entity);
    }

    @Transactional
    public boolean deleteDepartmentById(Long id) {
        if (id == null) {
            return false;
        }

        Department entity = departmentRepository.findById(id).orElse(null);
        if (entity == null) {
            return false;
        }
        departmentRepository.deleteByMpathStartingWith(entity.getMpath());

        return true;
    }

    @Transactional(readOnly = true)
    public Page<DepartmentDto> pagingDepartment(Pageable pageable, Long parentId, String keyword) {
        if (pageable == null) {
            return null;
        }
        if (!StringUtils.hasText(keyword)) {
            if (parentId == null) {
                return departmentRepository.findByParentIdIsNull(pageable);
            } else {
                return departmentRepository.findByParentId(parentId, pageable);
            }
        } else {
            return this.pagingDepartmentByKeyword(pageable, keyword);
        }
    }

    private Page<DepartmentDto> pagingDepartmentByKeyword(Pageable pageable, String keyword) {
        // Query 1 & 2: Chỉ lấy ID của các nút gốc ở trang hiện tại
        Page<Long> rootIdPage = departmentRepository.findRootIdsByKeyword(pageable, keyword);
        if (rootIdPage == null || rootIdPage.getContent().isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }

        // Query 3: Tìm các mpath của các department khớp từ khóa thuộc về danh sách rootId của trang hiện tại
        List<String> matchedMpaths = departmentRepository.findMpathByKeywordAndRootIds(keyword,
                rootIdPage.getContent());
        if (matchedMpaths == null || matchedMpaths.isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }

        // Lấy danh sách ID gốc của trang hiện tại dưới dạng Set
        Set<Long> currentPageRootIds = new HashSet<>(rootIdPage.getContent());

        // Thu thập tất cả các ID tổ tiên cần thiết để dựng cây
        Set<Long> idsToFetch = new HashSet<>();
        for (String mpath : matchedMpaths) {
            if (StringUtils.hasText(mpath)) {
                String[] pathParts = mpath.split("/");
                for (String part : pathParts) {
                    if (StringUtils.hasText(part)) {
                        idsToFetch.add(Long.valueOf(part));
                    }
                }
            }
        }

        if (idsToFetch.isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }

        // Query 4: Tải toàn bộ data chi tiết (Name, Code, ParentId...) của các node cần
        // thiết dựng cây cho trang hiện tại
        List<DepartmentDto> nodesToBuild = departmentRepository.findByIds(idsToFetch.stream().toList());
        if (nodesToBuild == null || nodesToBuild.isEmpty()) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }

        // Dựng cây từ danh sách node trên
        Map<Long, DepartmentDto> map = nodesToBuild.stream()
                .collect(Collectors.toMap(DepartmentDto::getId, dto -> dto, (existing, replacement) -> existing));

        List<DepartmentDto> roots = new ArrayList<>();

        for (DepartmentDto dto : nodesToBuild) {
            if (dto.getChildren() == null) {
                dto.setChildren(new ArrayList<>());
            }
            Long parentId = dto.getParentId();
            if (parentId != null && map.containsKey(parentId)) {
                DepartmentDto parentDto = map.get(parentId);
                if (parentDto.getChildren() == null) {
                    parentDto.setChildren(new ArrayList<>());
                }
                parentDto.getChildren().add(dto);
            } else {
                if (currentPageRootIds.contains(dto.getId())) {
                    roots.add(dto);
                }
            }
        }

        // Trả về trang dữ liệu với tổng số phần tử khớp với số nút gốc trên DB
        return new PageImpl<>(roots, pageable, rootIdPage.getTotalElements());
    }

    @Transactional
    public void generateFakeDepartments() {
        List<Long> createdIds = new ArrayList<>();
        Map<Long, String> idToMpathMap = new HashMap<>();
        Map<Long, Long> idToRootIdMap = new HashMap<>();

        // 1. Create exactly 100 root departments (parentId = null)
        for (int i = 1; i <= 100; i++) {
            Department entity = new Department();
            entity.setName("Phòng ban gốc " + i);
            entity.setCode("PB_ROOT_" + String.format("%03d", i));
            entity.setDescription("Mô tả phòng ban gốc " + i);
            entity.setMpath("");

            entity = departmentRepository.save(entity);
            entity.setMpath("/" + entity.getId() + "/");
            entity.setRootId(entity.getId());

            createdIds.add(entity.getId());
            idToMpathMap.put(entity.getId(), entity.getMpath());
            idToRootIdMap.put(entity.getId(), entity.getRootId());

            if (i % 50 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        // 2. Create a deep chain of at least 5 levels from the first root node
        if (!createdIds.isEmpty()) {
            Long currentParentId = createdIds.get(0);
            for (int level = 2; level <= 6; level++) {
                Department entity = new Department();
                entity.setName("Phòng ban con cấp " + level);
                entity.setCode("PB_CHAIN_" + level);
                entity.setDescription("Mô tả phòng ban cấp " + level);
                entity.setMpath("");

                Department parent = entityManager.getReference(Department.class, currentParentId);
                entity.setParent(parent);

                entity = departmentRepository.save(entity);

                String parentMpath = idToMpathMap.get(currentParentId);
                Long rootId = idToRootIdMap.get(currentParentId);

                entity.setMpath(parentMpath + entity.getId() + "/");
                entity.setRootId(rootId);

                createdIds.add(entity.getId());
                idToMpathMap.put(entity.getId(), entity.getMpath());
                idToRootIdMap.put(entity.getId(), entity.getRootId());

                currentParentId = entity.getId();
            }
        }

        // 3. Create the remaining departments (up to 10000 nodes total)
        int totalNodes = 10000;
        int currentCount = createdIds.size();
        for (int i = currentCount + 1; i <= totalNodes; i++) {
            Long parentId = createdIds.get((int) (Math.random() * createdIds.size()));

            Department entity = new Department();
            entity.setName("Phòng ban " + i);
            entity.setCode("PB_" + String.format("%04d", i));
            entity.setDescription("Mô tả phòng ban " + i);
            entity.setMpath("");

            Department parent = entityManager.getReference(Department.class, parentId);
            entity.setParent(parent);

            entity = departmentRepository.save(entity);

            String parentMpath = idToMpathMap.get(parentId);
            Long rootId = idToRootIdMap.get(parentId);

            entity.setMpath(parentMpath + entity.getId() + "/");
            entity.setRootId(rootId);

            createdIds.add(entity.getId());
            idToMpathMap.put(entity.getId(), entity.getMpath());
            idToRootIdMap.put(entity.getId(), entity.getRootId());

            if (i % 100 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }
}
