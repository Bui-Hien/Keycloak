package com.hienbx.keycloak.repository;

import com.hienbx.keycloak.dto.DepartmentDto;
import com.hienbx.keycloak.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long>, JpaSpecificationExecutor<Department> {

        @Query("SELECT new com.hienbx.keycloak.dto.DepartmentDto(entity) FROM Department entity WHERE entity.id IN(:ids)")
        List<DepartmentDto> findByIds(@Param("ids") List<Long> ids);

        @Query("SELECT new com.hienbx.keycloak.dto.DepartmentDto(entity) FROM Department entity WHERE (:parentId IS NULL AND entity.parentId IS NULL) OR (:parentId IS NOT NULL AND entity.parentId = :parentId)")
        Page<DepartmentDto> findByPage(Pageable pageable, @Param("parentId") Long parentId);

        @Query("SELECT new com.hienbx.keycloak.dto.DepartmentDto(entity) FROM Department entity WHERE entity.parentId IS NULL")
        Page<DepartmentDto> findByParentIdIsNull(Pageable pageable);

        @Query("SELECT new com.hienbx.keycloak.dto.DepartmentDto(entity) FROM Department entity WHERE entity.parentId = :parentId")
        Page<DepartmentDto> findByParentId(@Param("parentId") Long parentId, Pageable pageable);

        @Query("SELECT DISTINCT r.id FROM Department r WHERE r.parentId IS NULL AND EXISTS (" +
                        "SELECT 1 FROM Department d WHERE d.rootId = r.id " +
                        "AND (d.name LIKE CONCAT('%', :keyword, '%') " +
                                "OR d.code LIKE CONCAT('%', :keyword, '%') " +
                                "OR d.description LIKE CONCAT('%', :keyword, '%'))" +
                        ")")
        Page<Long> findRootIdsByKeyword(Pageable pageable, @Param("keyword") String keyword);

        @Modifying
        @Query("UPDATE Department d " +
                "SET d.mpath = CONCAT(:newMpath, SUBSTRING(d.mpath, LENGTH(:oldMpath) + 1, LENGTH(d.mpath)))" +
                ", d.rootId = :newRootId WHERE d.mpath LIKE CONCAT(:oldMpath, '%')")
        void updateMpathAndRootIdPrefix(@Param("oldMpath") String oldMpath, @Param("newMpath") String newMpath, @Param("newRootId") Long newRootId);

        @Modifying
        @Query("DELETE FROM Department d WHERE d.mpath LIKE CONCAT(:mpath, '%')")
        void deleteByMpathStartingWith(@Param("mpath") String mpath);

        @Query("SELECT d.mpath FROM Department d WHERE d.rootId IN :rootIds AND (d.name LIKE CONCAT('%', :keyword, '%') OR d.code LIKE CONCAT('%', :keyword, '%') OR d.description LIKE CONCAT('%', :keyword, '%'))")
        List<String> findMpathByKeywordAndRootIds(@Param("keyword") String keyword,
                        @Param("rootIds") List<Long> rootIds);
}
