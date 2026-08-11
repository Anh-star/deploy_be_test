package com.cmcu.itstudy.mapper;

import com.cmcu.itstudy.dto.menu.MenuDto;
import com.cmcu.itstudy.entity.Menu;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MenuMapper {

    private MenuMapper() {
    }

    public static MenuDto toMenuDto(Menu menu) {
        if (menu == null) {
            return null;
        }

        List<MenuDto> childDtos = null;
        if (menu.getChildren() != null) {
            childDtos = menu.getChildren()
                    .stream()
                    .map(MenuMapper::toMenuDto)
                    .collect(Collectors.toList());
        }

        return MenuDto.builder()
                .id(menu.getId() != null ? menu.getId().toString() : null)
                .name(menu.getName())
                .route(menu.getRoute())
                .children(childDtos)
                .build();
    }

    public static MenuDto toMenuDtoWithoutChildren(Menu menu) {
        if (menu == null) {
            return null;
        }

        return MenuDto.builder()
                .id(menu.getId() != null ? menu.getId().toString() : null)
                .name(menu.getName())
                .route(menu.getRoute())
                .parentId(menu.getParent() != null
                        ? menu.getParent().getId().toString()
                        : null)
                .children(null)
                .build();
    }

    /**
     * Build the DTO that represents an ancestor that was
     * included only because the user has permission for one
     * of its descendants. The frontend uses the
     * {@code wrapper} flag to decide whether to render the
     * node as a navigable link or as a structural group
     * heading.
     */
    public static MenuDto toWrapperDto(Menu menu) {
        if (menu == null) {
            return null;
        }
        MenuDto dto = toMenuDtoWithoutChildren(menu);
        dto.setWrapper(Boolean.TRUE);
        return dto;
    }

    public static String idOf(Menu menu) {
        if (menu == null || menu.getId() == null) {
            return null;
        }
        return menu.getId().toString();
    }

    public static UUID parseId(String id) {
        if (id == null) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}