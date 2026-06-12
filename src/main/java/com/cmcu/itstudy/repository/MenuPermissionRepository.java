package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.MenuPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MenuPermissionRepository extends JpaRepository<MenuPermission, UUID> {
    boolean existsByMenuIdAndPermissionId(UUID menuId, UUID permissionId);
}
