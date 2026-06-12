package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface MenuRepository extends JpaRepository<Menu, UUID> {

    List<Menu> findByParentIsNullOrderByDisplayOrderAsc();

    Optional<Menu> findByNameAndParent(String name, Menu parent);

    Optional<Menu> findByNameAndParentIsNull(String name);

    @Query("""
            select distinct m
            from Menu m
            join m.menuPermissions mp
            join mp.permission p
            where p.name in :permissionNames
            order by m.displayOrder asc
            """)
    List<Menu> findMenusByPermissionNames(@Param("permissionNames") List<String> permissionNames);
}

