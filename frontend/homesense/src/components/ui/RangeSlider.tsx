import { useId } from 'react';

interface RangeSliderProps {
  label: string;
  min: number;
  max: number;
  step?: number;
  valueMin: number;
  valueMax: number;
  onChange: (min: number, max: number) => void;
  /** 상한 손잡이가 max에 있을 때 "이상" 접미사를 붙이는 등 값 표시를 커스터마이즈한다. */
  formatValue?: (value: number) => string;
}

/**
 * UIC-04가 쓰는 3종 슬라이더(전용면적/거래금액/건축년도) 공용 구현. 두 개의 겹친 네이티브
 * `<input type="range">`로 두 손잡이를 표현한다(CSS로 각 입력의 썸만 클릭 가능하게 분리) — 커스텀
 * 포인터 드래그를 새로 짜는 대신 네이티브 요소를 쓴 이유는 키보드(화살표/Home/End)·스크린리더
 * 접근성을 공짜로 얻기 위해서다. 숫자 입력창도 함께 둬 정확한 값 직접 입력을 지원한다(모바일
 * 바텀시트뿐 아니라 데스크톱에서도 동일하게 유용해 분기하지 않았다).
 */
export function RangeSlider({ label, min, max, step = 1, valueMin, valueMax, onChange, formatValue }: RangeSliderProps) {
  const id = useId();
  const format = formatValue ?? ((v: number) => String(v));
  const percent = (v: number) => ((v - min) / (max - min)) * 100;

  const clamp = (v: number) => Math.min(Math.max(v, min), max);

  const handleMinChange = (raw: number) => {
    const next = Math.min(clamp(raw), valueMax);
    onChange(next, valueMax);
  };
  const handleMaxChange = (raw: number) => {
    const next = Math.max(clamp(raw), valueMin);
    onChange(valueMin, next);
  };

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <span className="text-[13px] font-semibold text-[#364153]">{label}</span>
        <span className="text-[12px] text-[#6a7282]">
          {format(valueMin)} ~ {valueMax >= max ? `${format(valueMax)} 이상` : format(valueMax)}
        </span>
      </div>

      <div className="relative h-5">
        <div className="absolute top-1/2 h-1 w-full -translate-y-1/2 rounded-full bg-[#e5e7eb]" />
        <div
          className="absolute top-1/2 h-1 -translate-y-1/2 rounded-full bg-brand"
          style={{ left: `${percent(valueMin)}%`, width: `${percent(valueMax) - percent(valueMin)}%` }}
        />
        <input
          type="range"
          aria-label={`${label} 최소값`}
          aria-valuetext={format(valueMin)}
          min={min}
          max={max}
          step={step}
          value={valueMin}
          onChange={(event) => handleMinChange(Number(event.target.value))}
          className="range-thumb-only absolute top-1/2 w-full -translate-y-1/2 appearance-none bg-transparent"
        />
        <input
          type="range"
          aria-label={`${label} 최대값`}
          aria-valuetext={valueMax >= max ? `${format(valueMax)} 이상` : format(valueMax)}
          min={min}
          max={max}
          step={step}
          value={valueMax}
          onChange={(event) => handleMaxChange(Number(event.target.value))}
          className="range-thumb-only absolute top-1/2 w-full -translate-y-1/2 appearance-none bg-transparent"
        />
      </div>

      <div className="mt-2.5 flex items-center gap-2">
        <input
          id={`${id}-min`}
          type="number"
          aria-label={`${label} 최소값 직접 입력`}
          min={min}
          max={valueMax}
          value={valueMin}
          onChange={(event) => handleMinChange(Number(event.target.value))}
          className="w-full min-w-0 rounded-[10px] border border-[#e5e7eb] px-2.5 py-1.5 text-[12px] text-[#101828] focus:border-brand focus:outline-none"
        />
        <span className="text-[#99a1af]">~</span>
        <input
          id={`${id}-max`}
          type="number"
          aria-label={`${label} 최대값 직접 입력`}
          min={valueMin}
          max={max}
          value={valueMax}
          onChange={(event) => handleMaxChange(Number(event.target.value))}
          className="w-full min-w-0 rounded-[10px] border border-[#e5e7eb] px-2.5 py-1.5 text-[12px] text-[#101828] focus:border-brand focus:outline-none"
        />
      </div>

      <style>{`
        .range-thumb-only {
          pointer-events: none;
        }
        .range-thumb-only::-webkit-slider-thumb {
          pointer-events: auto;
          appearance: none;
          width: 16px;
          height: 16px;
          border-radius: 9999px;
          background: #0f5c54;
          border: 2px solid white;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
          cursor: pointer;
        }
        .range-thumb-only::-moz-range-thumb {
          pointer-events: auto;
          width: 16px;
          height: 16px;
          border-radius: 9999px;
          background: #0f5c54;
          border: 2px solid white;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
          cursor: pointer;
        }
        .range-thumb-only::-webkit-slider-runnable-track,
        .range-thumb-only::-moz-range-track {
          background: transparent;
        }
      `}</style>
    </div>
  );
}
