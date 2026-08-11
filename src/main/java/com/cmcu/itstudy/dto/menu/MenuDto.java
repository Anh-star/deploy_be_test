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

    /**
     * Parent identifier, when present. The frontend uses this
     * field to distinguish a parent GROUP (a node that has at
     * least one visible descendant in the authorisation graph)
     * from a navigable LEAF (a node that is not declared as a
     * parent of any other node in the payload). When the value
     * is {@code null} the node is a root.
     */
    private String parentId;

    /**
     * {@code true} when the node is present in the response
     * only because it is an ancestor of a permitted menu (the
     * user does NOT have a direct permission for it). The
     * frontend uses this flag to decide whether to render the
     * node as a navigable link or as a wrapper group heading.
     */
    private Boolean wrapper;

    @Builder.Default
    private List<MenuDto> children = new ArrayList<>();
}
