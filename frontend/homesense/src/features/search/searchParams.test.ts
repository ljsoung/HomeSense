import { describe, expect, it } from 'vitest';
import { defaultFilters, keywordIssue, urlKeywordIssue } from './searchParams';

describe('keywordIssue — 서버 SearchKeywordPolicy와 같은 2~50자(코드포인트) 규칙', () => {
  it('공백뿐이거나 비어 있으면 검사 대상이 아니다', () => {
    expect(keywordIssue('')).toBeNull();
    expect(keywordIssue('   ')).toBeNull();
  });

  it('앞뒤 공백을 뺀 1자는 너무 짧다', () => {
    expect(keywordIssue('래')).toBe('tooShort');
    expect(keywordIssue('  래  ')).toBe('tooShort');
  });

  it('2자와 50자는 통과, 51자는 너무 길다', () => {
    expect(keywordIssue('수원')).toBeNull();
    expect(keywordIssue('가'.repeat(50))).toBeNull();
    expect(keywordIssue('가'.repeat(51))).toBe('tooLong');
  });

  it('이모지는 UTF-16 길이가 아니라 1자로 센다', () => {
    expect(keywordIssue('🏠')).toBe('tooShort');
    expect(keywordIssue('🏠'.repeat(50))).toBeNull();
  });
});

describe('urlKeywordIssue — URL로 들어온 검색 조건', () => {
  it('지역 코드가 있으면 API가 keyword를 싣지 않으므로 검사하지 않는다', () => {
    expect(urlKeywordIssue({ ...defaultFilters(), regionCode: '1111000000', keyword: '래' })).toBeNull();
  });

  it('keyword만 있으면 같은 규칙으로 검사한다', () => {
    expect(urlKeywordIssue({ ...defaultFilters(), keyword: '래' })).toBe('tooShort');
    expect(urlKeywordIssue({ ...defaultFilters(), keyword: '수원' })).toBeNull();
    expect(urlKeywordIssue(defaultFilters())).toBeNull();
  });
});
