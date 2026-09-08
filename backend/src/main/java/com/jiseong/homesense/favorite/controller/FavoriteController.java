package com.jiseong.homesense.favorite.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.common.response.ApiResponse;
import com.jiseong.homesense.common.security.UserPrincipal;
import com.jiseong.homesense.favorite.dto.AddFavoritePropertyRequest;
import com.jiseong.homesense.favorite.dto.AddFavoriteRegionRequest;
import com.jiseong.homesense.favorite.dto.FavoritePropertyResponse;
import com.jiseong.homesense.favorite.dto.FavoritePropertySummaryResponse;
import com.jiseong.homesense.favorite.dto.FavoriteRegionResponse;
import com.jiseong.homesense.favorite.dto.FavoriteRegionSummaryResponse;
import com.jiseong.homesense.favorite.service.FavoriteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** API-FAV-01. base path: /api/favorites. 전 엔드포인트 인증 필수(SecurityConfig). */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping("/properties")
    public ApiResponse<List<FavoritePropertySummaryResponse>> getFavoriteProperties(
            @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.success(favoriteService.getFavoriteProperties(me.userId()));
    }

    @PostMapping("/properties")
    public ApiResponse<FavoritePropertyResponse> addFavoriteProperty(
            @AuthenticationPrincipal UserPrincipal me, @RequestBody AddFavoritePropertyRequest request) {
        return ApiResponse.success(favoriteService.addFavoriteProperty(me.userId(), request.toCommand()));
    }

    @DeleteMapping("/properties/{id}")
    public ApiResponse<Void> removeFavoriteProperty(
            @AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        favoriteService.removeFavoriteProperty(me.userId(), id);
        return ApiResponse.success((Void) null);
    }

    @GetMapping("/regions")
    public ApiResponse<List<FavoriteRegionSummaryResponse>> getFavoriteRegions(
            @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.success(favoriteService.getFavoriteRegions(me.userId()));
    }

    @PostMapping("/regions")
    public ApiResponse<FavoriteRegionResponse> addFavoriteRegion(
            @AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody AddFavoriteRegionRequest request) {
        return ApiResponse.success(favoriteService.addFavoriteRegion(me.userId(), request.toCommand()));
    }

    @DeleteMapping("/regions/{id}")
    public ApiResponse<Void> removeFavoriteRegion(
            @AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        favoriteService.removeFavoriteRegion(me.userId(), id);
        return ApiResponse.success((Void) null);
    }
}
