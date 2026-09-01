package com.jiseong.homesense.batch.loader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.DealCategory;

/**
 * 테이블정의서 3.3절 업무 규칙: dedup_hash = SHA-256(housing_type | complex_id | deal_date | floor |
 * exclu_use_area | 금액) 해시. 금액 구간은 SALE이면 deal_amount, RENT면 deposit_amount+monthly_rent_amount다
 * — 매매/전월세가 서로 다른 금액 컬럼을 쓰므로 dealCategory로 분기해 하나의 금액 구간을 만든다.
 * complex_id/floor처럼 null일 수 있는 필드는 "null" 문자열로 치환해 넣는다 — 매칭 실패 건과 성공 건이
 * 같은 해시로 충돌하지 않아야 하므로 값이 없다는 사실 자체도 해시에 반영되어야 한다.
 */
@Component
class DedupHashCalculator {

    private static final String FIELD_DELIMITER = "|";
    private static final String SHA_256 = "SHA-256";

    String calculate(TradeDraft draft) {
        String amountSegment = draft.dealCategory() == DealCategory.SALE
                ? String.valueOf(draft.dealAmount())
                : draft.depositAmount() + "+" + draft.monthlyRentAmount();

        String raw = String.join(FIELD_DELIMITER,
                draft.housingType().name(),
                String.valueOf(draft.complexId()),
                draft.dealDate().toString(),
                String.valueOf(draft.floor()),
                draft.excluUseArea().toPlainString(),
                amountSegment);

        return sha256Hex(raw);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // 모든 JVM 구현체가 SHA-256을 필수로 제공해야 한다(JCA 표준 알고리즘) — 발생하면 배치를 계속
            // 진행할 수 없는 환경 결함이므로 unchecked로 감싸 그대로 드러낸다.
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없다", e);
        }
    }
}
