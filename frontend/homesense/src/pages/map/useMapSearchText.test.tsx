import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { useMapSearchText } from './useMapSearchText';

/** 주소창만 바꾼다(react-router의 replace가 히스토리를 바꾼 뒤, 전환 렌더가 아직 오지 않은 상태). */
function setLocationKeyword(keyword: string | null) {
  window.history.replaceState(null, '', keyword ? `/map?keyword=${encodeURIComponent(keyword)}` : '/map');
}

/** 렌더된 URL의 검색 조건을 prop으로 받는다 — 전환 렌더가 도착하는 순간을 rerender로 정한다. */
function mount(initial: string) {
  setLocationKeyword(initial || null);
  return renderHook(({ url }) => useMapSearchText(url), { initialProps: { url: initial } });
}

/** 화면이 검색할 때의 순서: 기억 → URL 갱신(주소창이 먼저 바뀐다). */
function searchOwn(result: { current: ReturnType<typeof useMapSearchText> }, text: string) {
  act(() => {
    result.current.rememberOwn(text);
    setLocationKeyword(text);
  });
}

describe('useMapSearchText', () => {
  beforeEach(() => setLocationKeyword(null));

  it('바깥에서 URL의 검색 조건이 바뀌면 검색창을 맞춘다', () => {
    const { result, rerender } = mount('래미안');
    act(() => result.current.setValue('입력 중'));
    setLocationKeyword(null);
    rerender({ url: '' });
    expect(result.current.value).toBe('');
  });

  it('자기 검색이 늦게 반영돼도 그사이 입력한 다음 검색어를 지우지 않는다', () => {
    const { result, rerender } = mount('');
    act(() => result.current.setValue('강남'));
    searchOwn(result, '강남');
    act(() => result.current.setValue('수원'));
    rerender({ url: '강남' });
    expect(result.current.value).toBe('수원');
  });

  it('주소창이 렌더보다 앞서 있는 동안(반영 대기 중) 다른 렌더가 와도 기억을 지우지 않는다', () => {
    const { result, rerender } = mount('');
    searchOwn(result, '강남');
    act(() => result.current.setValue('수원')); // 반영 전에 입력으로 다시 렌더
    rerender({ url: '' });
    rerender({ url: '강남' });
    expect(result.current.value).toBe('수원');
  });

  it('자기 검색 둘이 차례로 반영되고, 그 뒤 바깥 변경은 다시 맞춘다', () => {
    const { result, rerender } = mount('');
    searchOwn(result, '강남');
    searchOwn(result, '수원');
    act(() => result.current.setValue('입력 중'));
    rerender({ url: '강남' });
    rerender({ url: '수원' });
    expect(result.current.value).toBe('입력 중');
    setLocationKeyword('바깥');
    rerender({ url: '바깥' });
    expect(result.current.value).toBe('바깥');
  });

  it('같은 검색어를 다시 검색한 뒤 뒤로가기로 바뀐 URL에 검색창을 맞춘다', () => {
    const { result, rerender } = mount('이전');
    act(() => result.current.setValue('강남'));
    searchOwn(result, '강남');
    rerender({ url: '강남' });
    searchOwn(result, '강남'); // 같은 값 — URL이 바뀌지 않는다
    rerender({ url: '강남' });
    setLocationKeyword('이전'); // 뒤로가기
    rerender({ url: '이전' });
    expect(result.current.value).toBe('이전');
  });

  it('반영되지 않고 끝난 자기 값(T를 썼다 곧바로 원래 S로)은 비워, 나중에 바깥에서 T로 바뀌면 맞춘다', () => {
    const { result, rerender } = mount('S');
    searchOwn(result, 'T');
    searchOwn(result, 'S');
    // 전환 렌더가 합쳐져 URL 조건은 S 그대로 — 렌더된 URL과 주소창이 같다.
    rerender({ url: 'S' });
    act(() => result.current.setValue('입력 중'));
    setLocationKeyword('T'); // 바깥(뒤로가기 등)에서 T
    rerender({ url: 'T' });
    expect(result.current.value).toBe('T');
  });
});
