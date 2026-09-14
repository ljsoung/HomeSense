interface SegmentedToggleOption<T extends string> {
  value: T;
  label: string;
}

interface SegmentedToggleProps<T extends string> {
  options: SegmentedToggleOption<T>[];
  value: T;
  onChange: (value: T) => void;
}

/**
 * 히어로 검색의 매물유형/거래유형 토글. 작업 지시는 모바일에서 가로 스크롤 칩으로 바뀐다고
 * 가정했지만, 실제 Figma 모바일 로그인 프레임(24:6736)을 직접 조회해 보니 데스크톱과 동일하게
 * 두 줄 알약 버튼 그룹을 그대로 축소해 쓴다(가로 스크롤 아님) — 확인된 픽셀을 기준으로 구현했다.
 */
export function SegmentedToggle<T extends string>({ options, value, onChange }: SegmentedToggleProps<T>) {
  return (
    <div className="flex gap-1.5">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          className={`rounded-full px-3.5 py-1.5 text-[13px] font-semibold whitespace-nowrap transition-colors ${
            value === option.value ? 'bg-brand text-white' : 'bg-[#f2f3f5] text-[#6e7580]'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
