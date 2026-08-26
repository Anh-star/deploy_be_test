package com.cmcu.itstudy.mapper;

import com.cmcu.itstudy.dto.document.CategoryResponseDto;
import com.cmcu.itstudy.entity.Category;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static CategoryResponseDto toDto(Category category) {
        if (category == null) {
            return null;
        }

        return CategoryResponseDto.builder()
                .id(category.getId() != null ? category.getId().toString() : null)
                .name(category.getName())
                .slug(category.getSlug())
                .displayOrder(category.getDisplayOrder())
                .build();
    }
}
