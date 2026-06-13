package com.hienbx.keycloak.repository;

import com.hienbx.keycloak.dto.DepartmentDto;
import com.hienbx.keycloak.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    @Query("SELECT new com.hienbx.keycloak.dto.DepartmentDto(entity) FROM Department entity WHERE entity.id IN(:ids)")
    List<DepartmentDto> findByIds(@Param("ids") List<Long> ids);

    @Query("SELECT new com.hienbx.keycloak.dto.DepartmentDto(entity) FROM Department entity WHERE (:parentId IS NULL AND entity.parent IS NULL) OR (:parentId IS NOT NULL AND entity.parent.id = :parentId)")
    Page<DepartmentDto> findByPage(Pageable pageable, @Param("parentId") Long parentId);

    @Query("SELECT new com.hienbx.keycloak.dto.DepartmentDto(entity) FROM Department entity WHERE LOWER(entity.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(entity.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(entity.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<DepartmentDto> findByPage(Pageable pageable, @Param("keyword") String keyword);
}
