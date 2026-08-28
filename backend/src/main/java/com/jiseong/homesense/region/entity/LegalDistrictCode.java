package com.jiseong.homesense.region.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "legal_district_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class LegalDistrictCode {

    @Id
    @Column(name = "legal_dong_cd", length = 10)
    private String legalDongCd;

    @Column(name = "legal_dong_name", nullable = false, length = 60)
    private String legalDongName;

    @Column(name = "sido_name", length = 20)
    private String sidoName;

    @Column(name = "sigungu_name", length = 20)
    private String sigunguName;

    @Column(name = "eupmyeondong_name", length = 20)
    private String eupmyeondongName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "data_version", nullable = false)
    private LocalDate dataVersion;
}
