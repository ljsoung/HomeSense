package com.jiseong.homesense.batch.errorhandler;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

/**
 * RetryQueueManager가 재시도 시점에 RealEstateApiCollector.collect()를 다시 호출할 수 있도록
 * 조합 식별자만 담은 값 객체. BAT-CLC-01(collect)의 파라미터와 동일하다.
 */
public record CollectRequest(HousingType housingType, DealCategory dealCategory, String sggCd, String dealYmd) {
}
