package com.jiseong.homesense.notification.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-NTF-01.updateSettings() — favoritePropertyId/favoriteRegionId 둘 다 지정되지 않은 경우.
 * MY-03에서는 프론트가 저장 버튼을 비활성화해 사전 차단하는 것을 전제하므로, API를 직접 호출하는
 * 경우에만 발생이 예상된다.
 */
public class MissingTargetException extends BusinessException {

    public MissingTargetException() {
        super("MISSING_TARGET", "알림 설정 대상(favoritePropertyId 또는 favoriteRegionId)을 지정해야 합니다",
                HttpStatus.BAD_REQUEST);
    }
}
