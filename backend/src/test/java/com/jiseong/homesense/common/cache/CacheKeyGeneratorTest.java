package com.jiseong.homesense.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class CacheKeyGeneratorTest {

    private final CacheKeyGenerator generator = new CacheKeyGenerator();

    @Test
    void 파라미터가_하나면_그_값을_그대로_키로_쓴다() throws NoSuchMethodException {
        Method method = Dummy.class.getMethod("oneParam", Long.class);

        Object key = generator.generate(new Dummy(), method, 123L);

        assertThat(key).isEqualTo("123");
    }

    @Test
    void 파라미터가_여러개면_길이_접두로_이어붙인다() throws NoSuchMethodException {
        Method method = Dummy.class.getMethod("twoParams", String.class, int.class);

        Object key = generator.generate(new Dummy(), method, "gangnam", 20);

        assertThat(key).isEqualTo("7:gangnam2:20");
    }

    @Test
    void 파라미터가_없으면_메서드_이름을_키로_쓴다() throws NoSuchMethodException {
        Method method = Dummy.class.getMethod("noParams");

        Object key = generator.generate(new Dummy(), method);

        assertThat(key).isEqualTo("noParams");
    }

    @Test
    void 값에_구분자가_섞여도_서로_다른_파라미터_조합은_같은_키로_충돌하지_않는다() throws NoSuchMethodException {
        // 코드리뷰(PR #14)에서 지적된 시나리오: 단순 ":" join이었다면 ("a:b","c")와 ("a","b:c")가
        // 똑같이 "a:b:c"로 충돌해 요청 A가 요청 B의 캐시를 그대로 받아갔다.
        Method method = Dummy.class.getMethod("twoParams", String.class, int.class);
        Method stringPairMethod = Dummy.class.getMethod("twoStringParams", String.class, String.class);

        Object keyAB = generator.generate(new Dummy(), stringPairMethod, "a:b", "c");
        Object keyBA = generator.generate(new Dummy(), stringPairMethod, "a", "b:c");

        assertThat(keyAB).isNotEqualTo(keyBA);
    }

    @Test
    void null_파라미터는_리터럴_문자열_null과_충돌하지_않는다() throws NoSuchMethodException {
        Method method = Dummy.class.getMethod("twoStringParams", String.class, String.class);

        Object keyWithActualNull = generator.generate(new Dummy(), method, null, "x");
        Object keyWithLiteralNullString = generator.generate(new Dummy(), method, "null", "x");

        assertThat(keyWithActualNull).isNotEqualTo(keyWithLiteralNullString);
    }

    private static class Dummy {
        public void oneParam(Long id) {
        }

        public void twoParams(String query, int limit) {
        }

        public void twoStringParams(String a, String b) {
        }

        public void noParams() {
        }
    }
}
