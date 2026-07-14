package com.cmcu.itstudy.service.contract;

import java.util.UUID;

public interface SellerEarningReleaseService {

    boolean releaseIfDue(UUID earningId);
}