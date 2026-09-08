package com.jiseong.homesense.recentview.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.common.response.ApiResponse;
import com.jiseong.homesense.common.security.UserPrincipal;
import com.jiseong.homesense.recentview.dto.RecentViewResponse;
import com.jiseong.homesense.recentview.service.RecentViewService;

import lombok.RequiredArgsConstructor;

/**
 * API-RCV-01. base path: /api/recent-views. 비로그인 조회 허용(인증 불필요) — 로그인 시 토큰의
 * userId, 비로그인 시 X-Session-Id 헤더(프런트가 브라우저별로 생성해 관리하는 세션 식별자)
 * 기준으로 조회 주체를 결정한다. 등록 전용 API는 두지 않는다 — record()는 SVC-CPX-01의 단지
 * 상세 조회 경로(ComplexController.getDetail())에서 호출된다.
 */
@RestController
@RequestMapping("/api/recent-views")
@RequiredArgsConstructor
public class RecentViewController {

    private static final int DEFAULT_LIMIT = 3;

    /**
     * 비로그인 조회 주체 식별 헤더 — ComplexController.getDetail()이 record() 호출 시 같은 상수를
     * 참조한다. 두 곳이 서로 다른 헤더명을 쓰면 세션 사용자가 방금 기록한 조회를 다시는 조회하지
     * 못하는 조용한 버그가 생기므로 리터럴을 중복해 두지 않는다.
     */
    public static final String SESSION_ID_HEADER = "X-Session-Id";

    private final RecentViewService recentViewService;

    @GetMapping
    public ApiResponse<List<RecentViewResponse>> getRecentViews(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestHeader(value = SESSION_ID_HEADER, required = false) String sessionId,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        Long userId = me == null ? null : me.userId();
        return ApiResponse.success(recentViewService.getRecent(userId, sessionId, limit));
    }
}
