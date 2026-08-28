package com.jiseong.homesense.batch.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "batch_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchLog {

    private static final String PENDING_RESULT_CODE = "000";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_log_id")
    private Long batchLogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "housing_type", nullable = false, length = 10)
    private HousingType housingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "deal_category", nullable = false, length = 10)
    private DealCategory dealCategory;

    @Column(name = "lawd_cd", nullable = false, length = 5)
    private String lawdCd;

    @Column(name = "deal_ymd", nullable = false, length = 6)
    private String dealYmd;

    @Column(name = "dataset_id", nullable = false, length = 20)
    private String datasetId;

    @Column(name = "result_code", nullable = false, length = 3)
    private String resultCode;

    @Column(name = "result_message", length = 200)
    private String resultMessage;

    @Column(name = "success_yn", nullable = false)
    private boolean successYn;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private BatchLog(HousingType housingType, DealCategory dealCategory, String lawdCd, String dealYmd,
                      String datasetId, String resultCode, boolean successYn, int processedCount,
                      int errorCount, LocalDateTime startedAt) {
        this.housingType = housingType;
        this.dealCategory = dealCategory;
        this.lawdCd = lawdCd;
        this.dealYmd = dealYmd;
        this.datasetId = datasetId;
        this.resultCode = resultCode;
        this.successYn = successYn;
        this.processedCount = processedCount;
        this.errorCount = errorCount;
        this.startedAt = startedAt;
    }

    public static BatchLog start(HousingType housingType, DealCategory dealCategory, String lawdCd,
                                  String dealYmd, String datasetId) {
        return BatchLog.builder()
                .housingType(housingType)
                .dealCategory(dealCategory)
                .lawdCd(lawdCd)
                .dealYmd(dealYmd)
                .datasetId(datasetId)
                .resultCode(PENDING_RESULT_CODE)
                .successYn(false)
                .processedCount(0)
                .errorCount(0)
                .startedAt(LocalDateTime.now())
                .build();
    }

    public void finish(String resultCode, String resultMessage, boolean successYn, int processedCount, int errorCount) {
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
        this.successYn = successYn;
        this.processedCount = processedCount;
        this.errorCount = errorCount;
        this.finishedAt = LocalDateTime.now();
    }
}
