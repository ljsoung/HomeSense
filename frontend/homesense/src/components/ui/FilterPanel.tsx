import { useId } from 'react';
import { Checkbox } from './Checkbox';
import { RangeSlider } from './RangeSlider';
import { RotateCcwIcon } from '../icons/RotateCcwIcon';
import { AMOUNT_RANGE, AREA_RANGE, HOUSING_TYPE_OPTIONS, buildYearMax, BUILD_YEAR_MIN, type SearchFilters } from '../../features/search/searchParams';
import type { HousingType } from '../../features/complex/types';

interface FilterPanelProps {
  draft: SearchFilters;
  onChangeDraft: (updater: (prev: SearchFilters) => SearchFilters) => void;
  onApply: () => void;
  onReset: () => void;
  /**
   * "필터" 제목 + 상단 초기화 링크를 그릴지 여부(기본 true). 데스크톱/태블릿 사이드바는 이 헤더가
   * 필요하지만(Figma 4:1704/24:10388 — "필터" 제목 옆에 작은 "초기화" 링크가 있고, 패널 하단에도
   * 별도로 "초기화"+"필터 적용" 버튼 쌍이 있다), 모바일 바텀시트는 `BottomSheet`가 이미 자기 헤더에
   * "필터" 제목을 그려주므로 여기서 또 그리면 중복된다 — 그 경우 `false`로 끈다.
   */
  showHeader?: boolean;
}

const AMOUNT_LABEL: Record<SearchFilters['dealType'], string> = {
  매매: '거래금액',
  전세: '보증금',
  월세: '보증금',
};

const formatEok = (manwon: number) => {
  if (manwon <= 0) return '0원';
  const eok = manwon / 10000;
  return eok >= 1 ? `${eok % 1 === 0 ? eok : eok.toFixed(1)}억` : `${manwon.toLocaleString('ko-KR')}만원`;
};

/**
 * UIC-04 — 매물유형/거래유형/전용면적/거래금액(보증금)/건축년도 필터. SRCH-01의 데스크톱 사이드바와
 * 모바일 바텀시트가 이 컴포넌트 하나를 그대로 감싸 쓴다(레이아웃 컨테이너는 호출부 책임) —
 * MAP-01이 나중에 같은 필터 UI를 재사용할 수 있도록 SRCH-01 전용 요소(결과 카운트 등)는 이
 * 컴포넌트에 넣지 않았다. `draft`는 부모(SRCH-01 페이지)가 갖는 임시 상태이고, "필터 적용"을 눌러야
 * 실제 URL(=검색 조건)에 반영된다 — 이 패널 자체는 URL을 직접 건드리지 않는다.
 */
export function FilterPanel({ draft, onChangeDraft, onApply, onReset, showHeader = true }: FilterPanelProps) {
  // 라디오 그룹 이름은 인스턴스마다 달라야 한다 — SRCH-01 모바일은 숨겨진 데스크톱 사이드바와 바텀시트에
  // FilterPanel을 동시에 렌더하는데, 둘이 같은 name을 쓰면 브라우저가 6개 라디오를 한 그룹으로 묶어
  // 문서 전체에서 하나만 체크되게 한다. 그 결과 시트에서 거래유형을 바꾸면 체크가 숨겨진 사이드바
  // 쪽으로 넘어가 시트에는 아무것도 선택되지 않은 것처럼 보였다.
  const dealTypeGroupName = `dealType-${useId()}`;
  const toggleHousingType = (type: HousingType, checked: boolean) => {
    onChangeDraft((prev) => {
      const next = checked ? [...new Set([...prev.housingTypes, type])] : prev.housingTypes.filter((t) => t !== type);
      // 확정 사항 #8 — 최소 1개는 항상 선택된 상태를 유지한다(마지막 하나는 해제 불가).
      return next.length === 0 ? prev : { ...prev, housingTypes: next };
    });
  };

  return (
    <div className="flex flex-col gap-6">
      {showHeader && (
        <div className="flex items-center justify-between">
          <h3 className="text-[16px] font-bold text-[#101828]">필터</h3>
          <button type="button" onClick={onReset} className="flex items-center gap-1 text-[12px] text-[#99a1af] hover:text-[#6a7282]">
            <RotateCcwIcon />
            초기화
          </button>
        </div>
      )}
      <fieldset>
        <legend className="mb-2.5 text-[13px] font-semibold text-[#364153]">매물유형</legend>
        <div className="flex flex-col gap-2.5">
          {HOUSING_TYPE_OPTIONS.map((option) => (
            <Checkbox
              key={option.value}
              checked={draft.housingTypes.includes(option.value)}
              onChange={(checked) => toggleHousingType(option.value, checked)}
              label={option.label}
            />
          ))}
        </div>
      </fieldset>

      <fieldset>
        <legend className="mb-2.5 text-[13px] font-semibold text-[#364153]">거래유형</legend>
        <div role="radiogroup" aria-label="거래유형" className="flex flex-col gap-2.5">
          {(['매매', '전세', '월세'] as const).map((option) => (
            <label key={option} className="flex cursor-pointer items-center gap-2.5">
              <input
                type="radio"
                name={dealTypeGroupName}
                value={option}
                checked={draft.dealType === option}
                onChange={() => onChangeDraft((prev) => ({ ...prev, dealType: option }))}
                className="size-4 accent-brand"
              />
              <span className="text-[13px] text-[#364153]">{option}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <RangeSlider
        label="전용면적"
        min={AREA_RANGE.min}
        max={AREA_RANGE.max}
        valueMin={draft.areaMin}
        valueMax={draft.areaMax}
        onChange={(areaMin, areaMax) => onChangeDraft((prev) => ({ ...prev, areaMin, areaMax }))}
        formatValue={(v) => `${v}㎡`}
      />

      <RangeSlider
        label={AMOUNT_LABEL[draft.dealType]}
        min={AMOUNT_RANGE.min}
        max={AMOUNT_RANGE.max}
        step={1000}
        valueMin={draft.amountMin}
        valueMax={draft.amountMax}
        onChange={(amountMin, amountMax) => onChangeDraft((prev) => ({ ...prev, amountMin, amountMax }))}
        formatValue={formatEok}
      />

      <RangeSlider
        label="건축년도"
        min={BUILD_YEAR_MIN}
        max={buildYearMax()}
        valueMin={draft.buildYearMin}
        valueMax={draft.buildYearMax}
        onChange={(buildYearMin, buildYearMax) => onChangeDraft((prev) => ({ ...prev, buildYearMin, buildYearMax }))}
        formatValue={(v) => `${v}년`}
      />

      <div className="flex gap-2 pt-1">
        <button
          type="button"
          onClick={onReset}
          className="h-11 flex-1 rounded-[12px] border border-[#e5e7eb] text-[13.5px] font-semibold text-[#364153] hover:bg-[#f7f8fa]"
        >
          초기화
        </button>
        <button
          type="button"
          onClick={onApply}
          className="h-11 flex-[2] rounded-[12px] bg-brand text-[13.5px] font-bold text-white hover:bg-[#0d4f48]"
        >
          필터 적용
        </button>
      </div>
    </div>
  );
}
