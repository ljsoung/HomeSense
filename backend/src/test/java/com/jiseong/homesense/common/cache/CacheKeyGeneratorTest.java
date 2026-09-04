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
    void 파라미터가_여러개면_콜론으로_이어붙인다() throws NoSuchMethodException {
        Method method = Dummy.class.getMethod("twoParams", String.class, int.class);

        Object key = generator.generate(new Dummy(), method, "gangnam", 20);

        assertThat(key).isEqualTo("gangnam:20");
    }

    @Test
    void 파라미터가_없으면_메서드_이름을_키로_쓴다() throws NoSuchMethodException {
        Method method = Dummy.class.getMethod("noParams");

        Object key = generator.generate(new Dummy(), method);

        assertThat(key).isEqualTo("noParams");
    }

    private static class Dummy {
        public void oneParam(Long id) {
        }

        public void twoParams(String query, int limit) {
        }

        public void noParams() {
        }
    }
}
