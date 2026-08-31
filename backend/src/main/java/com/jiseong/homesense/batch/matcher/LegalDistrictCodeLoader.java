package com.jiseong.homesense.batch.matcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BAT-MAT-01. 행정안전부 법정동코드 전체자료 CSV를 legal_district_code 테이블에 최초/재적재한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalDistrictCodeLoader {

    // "CP949"라는 이름은 Java Charset 레지스트리에서 IBM949(EBCDIC 계열)로 매핑되어
    // 실제 윈도우 코드페이지 949와 다른 결과를 낸다. 동일한 인코딩을 가리키는 정확한 이름은 MS949.
    private static final Charset CSV_CHARSET = Charset.forName("MS949");
    private static final String EXISTING_STATUS = "존재";
    private static final String COL_CODE = "법정동코드";
    private static final String COL_NAME = "법정동명";
    private static final String COL_STATUS = "폐지여부";
    private static final int CODE_LENGTH = 10;
    private static final String SIDO_SUFFIX = "00000000"; // 3~10자리
    private static final String SGG_SUFFIX = "00000"; // 6~10자리

    private final LegalDistrictCodeRepository legalDistrictCodeRepository;

    @Transactional
    public void loadInitial(File csvFile) {
        LocalDate dataVersion = LocalDate.now();
        List<DongRecord> records = readExistingRecords(csvFile);

        // 시군구 대표행(6~10자리=00000)의 실제 이름을 마스터로 삼아 하위 행에서 그 접두어를 제거한다.
        // 시군구명이 "수원시 장안구"처럼 여러 토큰이거나(세종처럼) 아예 없는 경우도 정확히 처리하기 위함 —
        // 단순 공백 split로는 이 두 경우를 구분할 수 없다.
        Map<String, String> sidoNameByPrefix2 = buildSidoNameByPrefix2(records);
        Map<String, String[]> sggInfoByPrefix5 = buildSggInfoByPrefix5(records, sidoNameByPrefix2);

        List<LegalDistrictCode> rows = new ArrayList<>();
        for (DongRecord record : records) {
            String[] parts = resolveNameParts(record, sggInfoByPrefix5);
            rows.add(LegalDistrictCode.builder()
                    .legalDongCd(record.code())
                    .legalDongName(record.name())
                    .sidoName(parts[0])
                    .sigunguName(parts[1])
                    .eupmyeondongName(parts[2])
                    .isActive(true)
                    .dataVersion(dataVersion)
                    .build());
        }

        // 먼저 전부 비활성화한 뒤 이번 CSV에 실제로 존재하는 행만 upsert로 재활성화한다.
        // dataVersion(일 단위 해상도) 비교에 기대지 않으므로 같은 날 재적재해도 정확하다.
        legalDistrictCodeRepository.deactivateAll();
        legalDistrictCodeRepository.flush();
        legalDistrictCodeRepository.saveAll(rows);
        legalDistrictCodeRepository.flush();

        long inactiveCount = legalDistrictCodeRepository.count() - rows.size();
        log.info("법정동코드 적재 완료: {}건 (dataVersion={}), 비활성: {}건",
                rows.size(), dataVersion, inactiveCount);
    }

    private List<DongRecord> readExistingRecords(File csvFile) {
        List<DongRecord> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(csvFile.toPath()), CSV_CHARSET))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return records;
            }
            String delimiter = headerLine.contains("\t") ? "\t" : ",";
            Map<String, Integer> columnIndex = indexColumns(headerLine, delimiter);
            int codeIdx = requireColumn(columnIndex, COL_CODE);
            int nameIdx = requireColumn(columnIndex, COL_NAME);
            int statusIdx = requireColumn(columnIndex, COL_STATUS);
            int minColumns = Math.max(codeIdx, Math.max(nameIdx, statusIdx)) + 1;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split(delimiter, -1);
                if (fields.length < minColumns) {
                    continue;
                }
                if (!EXISTING_STATUS.equals(fields[statusIdx].trim())) {
                    continue;
                }

                // 법정동코드는 항상 문자열로 유지한다 — 숫자로 파싱하면 선행 0이 소실된다.
                String legalDongCd = fields[codeIdx].trim();
                String legalDongName = fields[nameIdx].trim();
                if (legalDongCd.length() != CODE_LENGTH) {
                    log.warn("법정동코드 길이가 {}자리가 아니어서 건너뜁니다: {}", CODE_LENGTH, legalDongCd);
                    continue;
                }
                records.add(new DongRecord(legalDongCd, legalDongName));
            }
        } catch (IOException e) {
            throw new IllegalStateException("법정동코드 CSV 적재 실패: " + csvFile.getAbsolutePath(), e);
        }
        return records;
    }

    private Map<String, String> buildSidoNameByPrefix2(List<DongRecord> records) {
        Map<String, String> sidoNameByPrefix2 = new HashMap<>();
        for (DongRecord record : records) {
            if (isSidoLevel(record.code())) {
                sidoNameByPrefix2.put(record.code().substring(0, 2), record.name());
            }
        }
        return sidoNameByPrefix2;
    }

    /**
     * prefix5(시군구 코드) -> {sido명, sigungu명(없으면 null), 시군구 대표행 원본 이름} 3종 세트.
     */
    private Map<String, String[]> buildSggInfoByPrefix5(List<DongRecord> records, Map<String, String> sidoNameByPrefix2) {
        Map<String, String[]> sggInfoByPrefix5 = new HashMap<>();
        for (DongRecord record : records) {
            if (!isSggLevel(record.code())) {
                continue;
            }
            String prefix2 = record.code().substring(0, 2);
            String sidoFull = sidoNameByPrefix2.get(prefix2);
            String sido;
            String sigungu;
            if (sidoFull != null && record.name().startsWith(sidoFull)) {
                sido = sidoFull;
                String remainder = record.name().substring(sidoFull.length()).trim();
                sigungu = remainder.isEmpty() ? null : remainder;
            } else {
                // 세종특별자치시처럼 별도 시도 전용 행이 없으면 이름 전체가 시도명이고 시군구 구분은 없다.
                sido = record.name();
                sigungu = null;
            }
            sggInfoByPrefix5.put(record.code().substring(0, 5), new String[]{sido, sigungu, record.name()});
        }
        return sggInfoByPrefix5;
    }

    private String[] resolveNameParts(DongRecord record, Map<String, String[]> sggInfoByPrefix5) {
        String code = record.code();
        String name = record.name();

        if (isSidoLevel(code)) {
            return new String[]{name, null, null};
        }
        if (isSggLevel(code)) {
            String[] info = sggInfoByPrefix5.get(code.substring(0, 5));
            return new String[]{info[0], info[1], null};
        }

        String[] info = sggInfoByPrefix5.get(code.substring(0, 5));
        if (info != null && name.startsWith(info[2])) {
            String eupmyeondong = name.substring(info[2].length()).trim();
            return new String[]{info[0], info[1], eupmyeondong.isEmpty() ? null : eupmyeondong};
        }

        log.warn("법정동코드 {}: 시군구 대표행을 찾지 못해 이름 토큰 분리로 대체합니다 ({})", code, name);
        return splitDongNameByTokens(name);
    }

    private boolean isSidoLevel(String code) {
        return code.substring(2).equals(SIDO_SUFFIX);
    }

    private boolean isSggLevel(String code) {
        return code.substring(5).equals(SGG_SUFFIX) && !isSidoLevel(code);
    }

    private Map<String, Integer> indexColumns(String headerLine, String delimiter) {
        String[] headers = headerLine.split(delimiter, -1);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            index.put(headers[i].trim(), i);
        }
        return index;
    }

    private int requireColumn(Map<String, Integer> columnIndex, String name) {
        Integer idx = columnIndex.get(name);
        if (idx == null) {
            throw new IllegalStateException("법정동코드 CSV에 필수 컬럼이 없습니다: " + name);
        }
        return idx;
    }

    /**
     * 시군구 대표행을 찾지 못했을 때만 쓰는 폴백. "시도 시군구 읍면동[ 리]" 형태를 단순 공백 기준으로 분리하며,
     * 시군구명이 여러 토큰인 경우(예: 수원시 장안구)나 시군구 구분이 없는 경우(세종)는 정확히 분리하지 못한다.
     */
    private String[] splitDongNameByTokens(String legalDongName) {
        String[] tokens = legalDongName.split("\\s+");
        String sido = tokens.length > 0 ? tokens[0] : null;
        String sigungu = tokens.length > 1 ? tokens[1] : null;
        String eupmyeondong = tokens.length > 2
                ? String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length))
                : null;
        return new String[]{sido, sigungu, eupmyeondong};
    }

    private record DongRecord(String code, String name) {
    }
}
