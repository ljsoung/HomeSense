package com.jiseong.homesense.batch.geocoding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * BAT-GEO-01. 카카오 주소 검색(GET /v2/local/search/address.json)으로 주소 한 건을 좌표로 바꾼다.
 * 빈 등록은 GeocodingConfig가 한다.
 *
 * <p>결과를 세 갈래로 나눈다.
 * <ul>
 *   <li>{@link Found} — documents[0]의 y(위도)·x(경도)를 DECIMAL(10,7) 자릿수로 맞춘다.</li>
 *   <li>{@link NotFound} — 결과 없음(documents 빈 배열) 또는 다시 보내도 같은 결과가 나올 4xx. 재시도하지 않는다.</li>
 *   <li>{@link GeocodingTransientException} — 429·5xx·네트워크 오류·응답 시간 초과. 재시도 대상.</li>
 * </ul>
 * 401·403은 키가 틀렸거나 카카오맵(로컬) 서비스가 앱에서 꺼진 경우라 단지마다 반복해도 같은 결과다 —
 * {@link GeocodingConfigurationException}으로 그 실행 전체를 멈추게 한다.
 */
public class KakaoGeocodingClient {

    static final String ADDRESS_SEARCH_PATH = "/v2/local/search/address.json";
    private static final int COORDINATE_SCALE = 7;

    private final RestClient restClient;

    public KakaoGeocodingClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public sealed interface GeocodeResult permits Found, NotFound {
    }

    public record Found(BigDecimal latitude, BigDecimal longitude) implements GeocodeResult {
    }

    /** @param reason 로그용 사유 — "NO_RESULT", "HTTP_400" 등 */
    public record NotFound(String reason) implements GeocodeResult {
    }

    public GeocodeResult geocode(String query) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(ADDRESS_SEARCH_PATH).queryParam("query", query).build())
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            return toResult(response.bodyTo(AddressSearchResponse.class));
                        }
                        if (status.value() == HttpStatus.UNAUTHORIZED.value()
                                || status.value() == HttpStatus.FORBIDDEN.value()) {
                            throw new GeocodingConfigurationException("카카오 주소 검색 인증 실패 HTTP " + status.value()
                                    + " — REST API 키와 카카오맵 서비스 활성화를 확인하라");
                        }
                        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value() || status.is5xxServerError()) {
                            throw new GeocodingTransientException("HTTP_" + status.value());
                        }
                        return new NotFound("HTTP_" + status.value());
                    });
        } catch (GeocodingConfigurationException | GeocodingTransientException e) {
            throw e;
        } catch (RestClientException e) {
            throw new GeocodingTransientException("IO_" + e.getClass().getSimpleName(), e);
        }
    }

    private static GeocodeResult toResult(AddressSearchResponse body) {
        if (body == null || body.documents() == null || body.documents().isEmpty()) {
            return new NotFound("NO_RESULT");
        }
        Document first = body.documents().get(0);
        if (first.x() == null || first.y() == null) {
            return new NotFound("NO_COORDINATE");
        }
        try {
            return new Found(new BigDecimal(first.y()).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
                    new BigDecimal(first.x()).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP));
        } catch (NumberFormatException e) {
            return new NotFound("NO_COORDINATE");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AddressSearchResponse(List<Document> documents) {
    }

    /** x = 경도, y = 위도(카카오 응답은 문자열). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Document(String x, String y) {
    }
}
