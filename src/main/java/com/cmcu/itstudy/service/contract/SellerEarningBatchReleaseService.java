package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.service.dto.SellerEarningBatchReleaseResult;

public interface SellerEarningBatchReleaseService {

    SellerEarningBatchReleaseResult releaseDueEarnings(int batchSize);
}