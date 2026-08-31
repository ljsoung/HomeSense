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

    private final LegalDistrictCodeRepository legalDistrictCodeRepository;

    @Transactional
    public void loadInitial(File csvFile) {
        LocalDate dataVersion = LocalDate.now();
        List<LegalDistrictCode> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(csvFile.toPath()), CSV_CHARSET))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
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
                String[] parts = splitDongName(legalDongName);

                rows.add(LegalDistrictCode.builder()
                        .legalDongCd(legalDongCd)
                        .legalDongName(legalDongName)
                        .sidoName(parts[0])
                        .sigunguName(parts[1])
                        .eupmyeondongName(parts[2])
                        .isActive(true)
                        .dataVersion(dataVersion)
                        .build());
            }
        } catch (IOException e) {
            throw new IllegalStateException("법정동코드 CSV 적재 실패: " + csvFile.getAbsolutePath(), e);
        }

        legalDistrictCodeRepository.saveAll(rows);
        log.info("법정동코드 적재 완료: {}건 (dataVersion={})", rows.size(), dataVersion);
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
     * "시도 시군구 읍면동[ 리]" 형태의 법정동명을 sido/sigungu/eupmyeondong(리 포함) 3단으로 분리한다.
     * 시도·시군구 대표 행은 뒤 토큰이 없어 sigungu/eupmyeondong이 null일 수 있다.
     */
    private String[] splitDongName(String legalDongName) {
        String[] tokens = legalDongName.split("\\s+");
        String sido = tokens.length > 0 ? tokens[0] : null;
        String sigungu = tokens.length > 1 ? tokens[1] : null;
        String eupmyeondong = tokens.length > 2
                ? String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length))
                : null;
        return new String[]{sido, sigungu, eupmyeondong};
    }
}
