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

       @Query("SELECT new com.hienbx.keycloak.dto.DepartmentDto(entity) FROM Department entity WHERE (:parentId IS NULL AND entity.parentId IS NULL) OR (:parentId IS NOT NULL AND entity.parentId = :parentId)")
       Page<DepartmentDto> findByPage(Pageable pageable, @Param("parentId") Long parentId);

       @Query("SELECT DISTINCT r.id FROM Department r WHERE r.parentId IS NULL AND EXISTS (" +
                     "SELECT 1 FROM Department d WHERE (d.name LIKE CONCAT('%', :keyword, '%') " +
                     "OR d.code LIKE CONCAT('%', :keyword, '%') " +
                     "OR d.description LIKE CONCAT('%', :keyword, '%')) " +
                     "AND d.mpath LIKE CONCAT(r.mpath, '%'))")
       Page<Long> findRootIdsByKeyword(Pageable pageable, @Param("keyword") String keyword);

       @Query("SELECT entity.mpath " +
                     "FROM Department entity " +
                     "WHERE entity.name LIKE CONCAT('%', :keyword, '%') " +
                     "   OR entity.code LIKE CONCAT('%', :keyword, '%') " +
                     "   OR entity.description LIKE CONCAT('%', :keyword, '%')")
       List<String> findAllMpathsByKeyword(@Param("keyword") String keyword);
}
