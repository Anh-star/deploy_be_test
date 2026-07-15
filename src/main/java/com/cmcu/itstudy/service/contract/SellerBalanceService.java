package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.entity.SellerBalance;

import java.util.UUID;

public interface SellerBalanceService {

    SellerBalance creditPending(UUID sellerId, Long amount, UUID earningId);

    SellerBalance movePendingToAvailable(UUID sellerId, Long amount, UUID earningId);

    SellerBalance reserveAvailableToLocked(UUID sellerId, Long amount);
}