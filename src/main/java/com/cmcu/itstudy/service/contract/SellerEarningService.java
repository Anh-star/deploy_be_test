package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.entity.SellerEarning;

import java.util.Optional;
import java.util.UUID;

public interface SellerEarningService {

    Optional<SellerEarning> recordSuccessfulPayment(UUID paymentId);
}
