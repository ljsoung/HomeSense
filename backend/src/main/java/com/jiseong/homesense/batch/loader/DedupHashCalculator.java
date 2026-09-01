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
 * <p>
 * complexId가 매칭된 건은 위 문서 원문 그대로 complex_id를 넣는다. 미매칭 건(complexId == null)은
 * 전부 같은 "null" 리터럴로 뭉치면 서로 다른 시군구/건물의 거래가 housing_type·deal_date·floor·
 * exclu_use_area·금액만 우연히 같아도 같은 해시로 충돌해 두 번째 건이 첫 번째 건의 UPDATE로 처리되며
 * 유실된다 — 코드리뷰에서 지적된 실제 결함이라 원본 지번 주소(sggCd/umdNm/buildingName/jibun)를 대체
 * 식별자로 함께 넣어 미매칭 건끼리도 구분되도록 한다. 매칭 성공 건의 해시 계산식(complex_id만 사용)은
 * 문서 원문 그대로 바꾸지 않는다.
 */
@Component
class DedupHashCalculator {

    private static final String FIELD_DELIMITER = "|";
    private static final String SHA_256 = "SHA-256";
    private static final String UNMATCHED_MARKER = "UNMATCHED";

    String calculate(TradeDraft draft) {
        String amountSegment = draft.dealCategory() == DealCategory.SALE
                ? String.valueOf(draft.dealAmount())
                : draft.depositAmount() + "+" + draft.monthlyRentAmount();

        String raw = String.join(FIELD_DELIMITER,
                draft.housingType().name(),
                complexSegment(draft),
                draft.dealDate().toString(),
                String.valueOf(draft.floor()),
                draft.excluUseArea().toPlainString(),
                amountSegment);

        return sha256Hex(raw);
    }

    private String complexSegment(TradeDraft draft) {
        if (draft.complexId() != null) {
            return String.valueOf(draft.complexId());
        }
        return String.join(FIELD_DELIMITER,
                UNMATCHED_MARKER,
                String.valueOf(draft.sggCd()),
                String.valueOf(draft.umdNm()),
                String.valueOf(draft.buildingName()),
                String.valueOf(draft.jibun()));
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
