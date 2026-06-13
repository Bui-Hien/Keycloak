package com.hienbx.keycloak.dto;

import com.hienbx.keycloak.entity.Department;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDto {
    private Long id;

    private String name;

    private String code;

    private String description;

    private String mpath;

    private Long parentId;

    @Builder.Default
    private List<DepartmentDto> children = new java.util.ArrayList<>();

    public DepartmentDto(Department entity) {
        if (entity == null) {
            return;
        }
        this.id = entity.getId();
        this.name = entity.getName();
        this.code = entity.getCode();
        this.description = entity.getDescription();
        this.mpath = entity.getMpath();
        if (entity.getParent() != null) {
            this.parentId = entity.getParent().getId();
        }
    }
}
