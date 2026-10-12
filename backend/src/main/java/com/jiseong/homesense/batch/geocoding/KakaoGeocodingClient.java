package com.jiseong.homesense.batch.geocoding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * BAT-GEO-01. 카카오 주소 검색(GET /v2/local/search/address.json)으로 주소 한 건을 좌표로 바꾼다. 주소 검색이 못 찾은
 * 단지는 키워드 장소 검색({@link #searchKeyword})으로 한 번 더 찾는다.
 * 빈 등록은 GeocodingConfig가 한다.
 *
 * <p>결과를 세 갈래로 나눈다.
 * <ul>
 *   <li>{@link Found} — documents[0]의 y(위도)·x(경도)를 DECIMAL(10,7) 자릿수로 맞춘다.</li>
 *   <li>{@link NotFound} — 결과 없음(documents 빈 배열), 유일해야 하는데 결과가 여럿, 또는 다시 보내도 같은 결과가 나올
 *       4xx. 재시도하지 않는다.</li>
 *   <li>{@link GeocodingTransientException} — 5xx·네트워크 오류·응답 시간 초과. 백오프 후 재시도 대상.</li>
 * </ul>
 * 다음 둘은 그 실행 전체를 즉시 멈추게 한다(재시도하지 않는다).
 * <ul>
 *   <li>401·403 → {@link GeocodingConfigurationException} — 키가 틀렸거나 카카오맵(로컬) 서비스가 앱에서 꺼진 경우라
 *       단지마다 반복해도 같은 결과다.</li>
 *   <li>429 → {@link GeocodingRateLimitedException} — 카카오 쪽 요청 한도. 계속 보내면 호출 수만 늘고, 일일 쿼터 초과라면
 *       그날은 다시 보내도 소용없다.</li>
 * </ul>
 */
public class KakaoGeocodingClient {

    static final String ADDRESS_SEARCH_PATH = "/v2/local/search/address.json";
    static final String KEYWORD_SEARCH_PATH = "/v2/local/search/keyword.json";
    private static final int COORDINATE_SCALE = 7;

    private final RestClient restClient;

    public KakaoGeocodingClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public sealed interface GeocodeResult permits Found, NotFound {
    }

    public record Found(BigDecimal latitude, BigDecimal longitude) implements GeocodeResult {
    }

    /** @param reason 로그용 사유 — "NO_RESULT", "AMBIGUOUS", "HTTP_400" 등 */
    public record NotFound(String reason) implements GeocodeResult {
    }

    /**
     * 키워드 장소 검색 결과 한 건(BAT-GEO-01 보완 경로). 좌표가 숫자가 아닌 항목은 버린다.
     *
     * @param addressName 지번 주소("서울 강남구 대치동 1032") — 시도는 카카오 표기(서울·경기·충남 등)다
     */
    public record KeywordPlace(String placeName, String categoryName, String addressName, BigDecimal latitude,
            BigDecimal longitude) {
    }

    /**
     * @param requireUnique true면 결과가 정확히 하나일 때만 좌표를 쓴다(여럿이면 {@code NotFound("AMBIGUOUS")}). false면
     *                      documents[0]을 쓴다(설계서 4.8절).
     */
    public GeocodeResult geocode(String query, boolean requireUnique) {
        Response<AddressSearchResponse> response = call(ADDRESS_SEARCH_PATH, query, AddressSearchResponse.class);
        if (!response.successful()) {
            return new NotFound("HTTP_" + response.status());
        }
        return toResult(response.body(), requireUnique);
    }

    /**
     * 키워드 장소 검색(GET /v2/local/search/keyword.json). 상태 코드 처리는 {@link #geocode}와 같다 — 429·401·403은 실행 중단
     * 예외, 5xx·네트워크 오류는 재시도 대상 예외. 다시 보내도 같을 4xx는 빈 목록이다. 후보를 고르는 규칙은 호출자
     * ({@link KeywordPlaceMatcher})가 맡는다.
     */
    public List<KeywordPlace> searchKeyword(String query) {
        Response<KeywordSearchResponse> response = call(KEYWORD_SEARCH_PATH, query, KeywordSearchResponse.class);
        if (!response.successful() || response.body() == null || response.body().documents() == null) {
            return List.of();
        }
        List<KeywordPlace> places = new ArrayList<>();
        for (PlaceDocument d : response.body().documents()) {
            BigDecimal latitude = coordinate(d.y());
            BigDecimal longitude = coordinate(d.x());
            if (latitude != null && longitude != null) {
                places.add(new KeywordPlace(d.placeName(), d.categoryName(), d.addressName(), latitude, longitude));
            }
        }
        return places;
    }

    /** 2xx면 body, 다시 보내도 같을 4xx면 body 없이 status만 담는다. */
    private record Response<B>(B body, int status) {
        boolean successful() {
            return status >= 200 && status < 300;
        }
    }

    private <B> Response<B> call(String path, String query, Class<B> bodyType) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path).queryParam("query", query).build())
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            return new Response<>(response.bodyTo(bodyType), status.value());
                        }
                        if (status.value() == HttpStatus.UNAUTHORIZED.value()
                                || status.value() == HttpStatus.FORBIDDEN.value()) {
                            throw new GeocodingConfigurationException("카카오 로컬 API 인증 실패 HTTP " + status.value()
                                    + " — REST API 키와 카카오맵 서비스 활성화를 확인하라");
                        }
                        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                            throw new GeocodingRateLimitedException("카카오 로컬 API 요청 한도 초과 HTTP 429 — 콘솔 쿼터와 "
                                    + "homesense.geocoding.daily-limit을 확인하라");
                        }
                        if (status.is5xxServerError()) {
                            throw new GeocodingTransientException("HTTP_" + status.value());
                        }
                        return new Response<B>(null, status.value());
                    });
        } catch (GeocodingConfigurationException | GeocodingRateLimitedException | GeocodingTransientException e) {
            throw e;
        } catch (RestClientException e) {
            throw new GeocodingTransientException("IO_" + e.getClass().getSimpleName(), e);
        }
    }

    private static GeocodeResult toResult(AddressSearchResponse body, boolean requireUnique) {
        if (body == null || body.documents() == null || body.documents().isEmpty()) {
            return new NotFound("NO_RESULT");
        }
        if (requireUnique && body.documents().size() > 1) {
            return new NotFound("AMBIGUOUS");
        }
        Document first = body.documents().get(0);
        BigDecimal latitude = coordinate(first.y());
        BigDecimal longitude = coordinate(first.x());
        if (latitude == null || longitude == null) {
            return new NotFound("NO_COORDINATE");
        }
        return new Found(latitude, longitude);
    }

    private static BigDecimal coordinate(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AddressSearchResponse(List<Document> documents) {
    }

    /** x = 경도, y = 위도(카카오 응답은 문자열). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Document(String x, String y) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KeywordSearchResponse(List<PlaceDocument> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlaceDocument(@JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName, @JsonProperty("address_name") String addressName,
            String x, String y) {
    }
}
