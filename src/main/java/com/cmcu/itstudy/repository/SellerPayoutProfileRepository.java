package com.cmcu.itstudy.repository;

import com.cmcu.itstudy.entity.SellerPayoutProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SellerPayoutProfileRepository
        extends JpaRepository<SellerPayoutProfile, UUID> {
}
