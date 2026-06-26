package com.cmcu.itstudy.dto.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuDto {

    private String id;

    private String name;

    private String route;

    private Integer displayOrder;

    private String icon;

    @Builder.Default
    private List<MenuDto> children = new ArrayList<>();
}
