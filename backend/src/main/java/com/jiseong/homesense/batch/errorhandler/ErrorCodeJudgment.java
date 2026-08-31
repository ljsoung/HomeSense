package com.jiseong.homesense.batch.errorhandler;

/**
 * CLAUDE.md 에러코드 판정표(요구사항정의서 원문)에 대응하는 판정 결과.
 */
public enum ErrorCodeJudgment {
    /** 000(정상), 03(데이터 없음) — 계속 진행. */
    CONTINUE,
    /** 01,02,04,05(서비스 장애), 22(트래픽 초과) — 지수 백오프 후 재시도. */
    RETRY,
    /** 10,11,12,20,32(설정/승인 문제) — 해당 조합만 중단. */
    ABORT_COMBINATION,
    /** 30,31(서비스키 오류/만료) — 배치 전체 조기 중단 + 알림. */
    ABORT_BATCH
}
