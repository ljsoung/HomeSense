package com.jiseong.homesense.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.notification.entity.NotificationSetting;
import com.jiseong.homesense.notification.repository.NotificationSettingRepository;

import lombok.RequiredArgsConstructor;

/**
 * NotificationService.upsertForProperty()/upsertForRegion()의 신규 INSERT 시도만 별도 REQUIRES_NEW
 * 트랜잭션(별도 EntityManager)에서 실행한다. JPA 스펙상 flush() 실패는(uk_ntf_setting_user_property/
 * uk_ntf_setting_user_region UNIQUE 충돌 포함) 현재 트랜잭션을 rollback-only로 표시한다 — 이는
 * MariaDB가 문장 단위 실패로 트랜잭션 전체를 중단시키지 않는 것과는 별개로, JPA 구현체(Hibernate)가
 * 스펙에 따라 강제하는 동작이다. INSERT를 updateSettings()의 트랜잭션과 같은 EntityManager에서
 * 시도하면, 충돌이 나는 순간 그 트랜잭션 전체가 rollback-only로 표시돼 이어지는 재조회·갱신이
 * 커밋 시점에 UnexpectedRollbackException으로 무효화된다 — 최종 상태가 요청 조건과 같아야 한다는
 * upsert 의도 자체가 깨진다. REQUIRES_NEW로 분리하면 이 INSERT가 실패해도 롤백되는 트랜잭션·
 * EntityManager는 이 메서드 안에서 새로 만든 것뿐이라, updateSettings()의 트랜잭션은 오염되지 않고
 * 그대로 재조회·갱신을 진행할 수 있다(TradeChunkLoader.upsertOne()/TradeInsertGateway와 동일한
 * 패턴, CLAUDE.md SVC-NTF-01 절 참고 — 최초 구현이 이 격리 없이 같은 트랜잭션에서 재조회를 시도해
 * 코드리뷰에서 지적됐다).
 */
@Component
@RequiredArgsConstructor
class NotificationSettingInsertGateway {

    private final NotificationSettingRepository notificationSettingRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(NotificationSetting setting) {
        notificationSettingRepository.saveAndFlush(setting);
    }
}
