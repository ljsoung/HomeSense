import { act, cleanup, fireEvent, render, renderHook, screen } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SearchBar } from '../../components/ui/SearchBar';
import { RegionAdder } from '../../pages/favorites/RegionAdder';
import { autocompleteRegions } from './api';
import type { RegionAutocompleteResponse } from './types';
import { AUTOCOMPLETE_DEBOUNCE_MS, useRegionAutocomplete } from './useRegionAutocomplete';

// 입력을 바꾼 뒤 디바운스·요청 동안 이전 검색어의 후보가 남아 클릭·Enter로 선택되던 문제(Codex P2)와,
// 늦게 도착한 이전 검색어의 응답이 새 목록을 덮는 경우를 막는지 본다.

vi.mock('./api', () => ({ autocompleteRegions: vi.fn() }));

interface PendingRequest {
  query: string;
  resolve: (items: RegionAutocompleteResponse[]) => void;
}

// 요청을 붙잡아 두고 테스트가 원하는 순서로 응답한다. 취소 신호는 무시한다 — 취소가 늦거나 빠져도
// 저장 시점의 검색어 비교가 이전 응답을 버리는지 확인하려는 것이다.
let pending: PendingRequest[] = [];

const region = (legalDongCd: string, fullPath: string): RegionAutocompleteResponse => ({ legalDongCd, fullPath });
const YEOKSAM = region('1168010100', '서울특별시 강남구 역삼동');
const BANPO = region('1165010700', '서울특별시 서초구 반포동');

function respond(query: string, items: RegionAutocompleteResponse[]) {
  const request = pending.find((entry) => entry.query === query);
  if (!request) throw new Error(`요청 없음: ${query}`);
  pending = pending.filter((entry) => entry !== request);
  request.resolve(items);
}

async function flush() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

function advanceDebounce() {
  act(() => {
    vi.advanceTimersByTime(AUTOCOMPLETE_DEBOUNCE_MS);
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  pending = [];
  vi.mocked(autocompleteRegions).mockImplementation(
    (query: string) =>
      new Promise<RegionAutocompleteResponse[]>((resolve) => {
        pending.push({ query, resolve });
      }),
  );
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.mocked(autocompleteRegions).mockReset();
});

describe('useRegionAutocomplete — 보이는 후보는 지금 입력의 응답에서만', () => {
  function setup() {
    return renderHook(({ value }) => useRegionAutocomplete(value, true), { initialProps: { value: '역삼' } });
  }

  it('입력을 바꾸면 디바운스 전이라도 이전 검색어의 후보가 보이지 않는다', async () => {
    const { result, rerender } = setup();
    advanceDebounce();
    respond('역삼', [YEOKSAM]);
    await flush();
    expect(result.current.items).toEqual([YEOKSAM]);
    expect(result.current.open).toBe(true);

    rerender({ value: '반포' });
    expect(result.current.items).toEqual([]);
    expect(result.current.open).toBe(false);
    expect(result.current.result).toBeNull();
  });

  it('입력을 바꾼 뒤에는 이전 검색어의 후보를 보이는 후보로 보지 않는다(선택 방어)', async () => {
    const { result, rerender } = setup();
    advanceDebounce();
    respond('역삼', [YEOKSAM]);
    await flush();
    expect(result.current.isVisible(YEOKSAM)).toBe(true);

    rerender({ value: '반포' });
    expect(result.current.isVisible(YEOKSAM)).toBe(false);
  });

  it('이전 검색어의 응답이 새 검색어의 응답보다 늦게 와도 새 목록이 유지된다', async () => {
    const { result, rerender } = setup();
    advanceDebounce();
    rerender({ value: '반포' });
    advanceDebounce();

    respond('반포', [BANPO]);
    await flush();
    respond('역삼', [YEOKSAM]);
    await flush();
    expect(result.current.result?.query).toBe('반포');
    expect(result.current.items).toEqual([BANPO]);
  });

  it('새 검색어의 디바운스 중에 이전 검색어의 응답이 와도 목록을 채우지 않는다', async () => {
    const { result, rerender } = setup();
    advanceDebounce();
    rerender({ value: '반포' });
    respond('역삼', [YEOKSAM]);
    await flush();
    expect(result.current.items).toEqual([]);
    expect(result.current.open).toBe(false);
  });

  it('입력을 바꾸면 활성 후보가 지워지고, 같은 검색어로 돌아와도 이어받지 않는다', async () => {
    const { result, rerender } = setup();
    advanceDebounce();
    respond('역삼', [YEOKSAM]);
    await flush();
    act(() => result.current.moveActive(1));
    expect(result.current.activeItem).toEqual(YEOKSAM);

    rerender({ value: '반포' });
    expect(result.current.activeIndex).toBe(-1);
    expect(result.current.activeItem).toBeNull();

    rerender({ value: '역삼' });
    expect(result.current.activeIndex).toBe(-1);
  });
});

describe('RegionAdder(MY-02) — 이전 검색어의 후보로 등록하지 않는다', () => {
  function type(input: HTMLElement, value: string) {
    fireEvent.change(input, { target: { value } });
  }

  it('활성 후보가 있던 상태에서 입력을 바꾸고 바로 Enter를 눌러도 선택되지 않는다', async () => {
    const onAdd = vi.fn(async () => {});
    render(<RegionAdder registeredCodes={new Set()} onAdd={onAdd} />);
    const input = screen.getByRole('combobox');
    fireEvent.focus(input);
    type(input, '역삼');
    advanceDebounce();
    respond('역삼', [YEOKSAM]);
    await flush();
    expect(screen.getAllByRole('option')).toHaveLength(1);
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(input.getAttribute('aria-activedescendant')).not.toBeNull();

    type(input, '반포');
    expect(screen.queryAllByRole('option')).toHaveLength(0);
    expect(input.getAttribute('aria-activedescendant')).toBeNull();
    fireEvent.keyDown(input, { key: 'Enter' });
    expect((input as HTMLInputElement).value).toBe('반포');
    expect(screen.getByRole('button', { name: '추가' })).toHaveProperty('disabled', true);
    expect(onAdd).not.toHaveBeenCalled();
  });

  it('"지역을 찾을 수 없습니다"는 지금 검색어의 응답이 0건일 때만 보인다', async () => {
    render(<RegionAdder registeredCodes={new Set()} onAdd={vi.fn(async () => {})} />);
    const input = screen.getByRole('combobox');
    fireEvent.focus(input);
    type(input, '없는동');
    advanceDebounce();
    respond('없는동', []);
    await flush();
    expect(screen.queryByText('지역을 찾을 수 없습니다')).not.toBeNull();

    type(input, '역삼');
    expect(screen.queryByText('지역을 찾을 수 없습니다')).toBeNull();
    advanceDebounce();
    respond('역삼', [YEOKSAM]);
    await flush();
    expect(screen.queryByText('지역을 찾을 수 없습니다')).toBeNull();
  });
});

describe('SearchBar(UIC-03) — 이전 검색어의 후보로 지역 검색하지 않는다', () => {
  it('활성 후보가 있던 상태에서 입력을 바꾸고 바로 Enter를 누르면 지역이 아니라 지금 입력으로 검색한다', async () => {
    const onSelectRegion = vi.fn();
    const onSubmitKeyword = vi.fn();
    function Harness() {
      const [value, setValue] = useState('');
      return (
        <SearchBar value={value} onChange={setValue} onSelectRegion={onSelectRegion} onSubmitKeyword={onSubmitKeyword} />
      );
    }
    render(<Harness />);
    const input = screen.getByRole('combobox');
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: '역삼' } });
    advanceDebounce();
    respond('역삼', [YEOKSAM]);
    await flush();
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(input.getAttribute('aria-activedescendant')).not.toBeNull();

    fireEvent.change(input, { target: { value: '반포' } });
    expect(screen.queryAllByRole('option')).toHaveLength(0);
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(onSelectRegion).not.toHaveBeenCalled();
    expect(onSubmitKeyword).toHaveBeenCalledWith('반포');
  });
});
