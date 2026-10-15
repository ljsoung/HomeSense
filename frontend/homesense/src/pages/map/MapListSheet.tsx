import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react';
import { ChevronDownIcon } from '../../components/icons/ChevronDownIcon';

export type SheetSnap = 'collapsed' | 'half' | 'full';

const SNAP_ORDER: SheetSnap[] = ['collapsed', 'half', 'full'];
const SNAP_LABEL: Record<SheetSnap, string> = { collapsed: '접힘', half: '반', full: '전체' };
/** 접힘 = 손잡이와 제목만(Figma 26:14325 높이 50 + 여백), 반 = Figma 26:14324 높이 210. 전체는 지도 영역의 85%. */
const COLLAPSED_PX = 58;
const HALF_PX = 210;
const FULL_RATIO = 0.85;

interface MapListSheetProps {
  snap: SheetSnap;
  onSnapChange: (snap: SheetSnap) => void;
  title: ReactNode;
  children: ReactNode;
}

function heightFor(snap: SheetSnap, containerHeight: number): number {
  if (snap === 'collapsed') return COLLAPSED_PX;
  if (snap === 'half') return Math.min(HALF_PX, containerHeight);
  return Math.max(HALF_PX, Math.round(containerHeight * FULL_RATIO));
}

/**
 * MAP-01 모바일 목록 — 지도 하단 Bottom Sheet(Figma 26:14324). 높이는 접힘/반/전체 세 단계이고, 손잡이를 끌어 바꾸거나
 * (놓으면 가까운 단계로 붙는다) 손잡이 버튼을 키보드로 조작한다(↑ 펼치기, ↓ 접기, Enter·Space 다음 단계). 지도 위에 겹쳐
 * 그려 지도 컨테이너 크기는 바뀌지 않는다 — 그래서 높이가 바뀌어도 map.relayout()이 필요 없다(패널 접기·창 크기 변경은
 * KakaoMapView의 ResizeObserver가 처리한다).
 *
 * 필터 바텀시트(BottomSheet, 모달)와 달리 페이지의 일부라 포커스를 가두지 않는다.
 */
export function MapListSheet({ snap, onSnapChange, title, children }: MapListSheetProps) {
  const sheetRef = useRef<HTMLDivElement>(null);
  const [dragHeight, setDragHeight] = useState<number | null>(null);
  const [containerHeight, setContainerHeight] = useState(600);
  // 끄는 중의 최신 높이. pointerup이 마지막 pointermove의 렌더보다 먼저 올 수 있어 state가 아니라 ref로 읽는다.
  const dragStart = useRef<{ y: number; height: number; dragging: boolean; latest: number } | null>(null);

  // 시트가 겹쳐 있는 영역(지도 영역)의 높이 — 전체 단계 높이와 끌기 상한에 쓴다.
  useEffect(() => {
    const parent = sheetRef.current?.parentElement;
    if (!parent) return;
    const measure = () => setContainerHeight(parent.clientHeight || 600);
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(measure);
    observer.observe(parent);
    return () => observer.disconnect();
  }, []);

  const height = dragHeight ?? heightFor(snap, containerHeight);

  const step = (delta: 1 | -1) => {
    const index = SNAP_ORDER.indexOf(snap) + delta;
    if (index >= 0 && index < SNAP_ORDER.length) onSnapChange(SNAP_ORDER[index]);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      step(1);
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      step(-1);
    }
  };

  // 끌기는 손잡이 영역에서 시작하지만 곧 시트 밖으로 나가므로 window에서 이어 받는다. 포인터 캡처는 쓰지 않는다 — 캡처하면
  // 손잡이·화살표 버튼의 click이 오지 않는다. 6px 넘게 움직여야 끌기로 본다(그 전에 놓으면 버튼 탭).
  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    const start = { y: event.clientY, height, dragging: false, latest: height };
    dragStart.current = start;
    const full = heightFor('full', containerHeight);

    const onMove = (e: globalThis.PointerEvent) => {
      if (!start.dragging) {
        if (Math.abs(e.clientY - start.y) < 6) return;
        start.dragging = true;
      }
      const next = Math.max(COLLAPSED_PX, Math.min(start.height + (start.y - e.clientY), full));
      start.latest = next;
      setDragHeight(next);
    };
    const finish = () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
      dragStart.current = null;
      if (!start.dragging) return;
      const nearest = SNAP_ORDER.reduce((best, candidate) =>
        Math.abs(heightFor(candidate, containerHeight) - start.latest) < Math.abs(heightFor(best, containerHeight) - start.latest)
          ? candidate
          : best,
      );
      setDragHeight(null);
      onSnapChange(nearest);
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', finish);
    window.addEventListener('pointercancel', finish);
  };

  return (
    <section
      ref={sheetRef}
      aria-label="주변 단지 목록"
      data-testid="map-list-sheet"
      data-snap={snap}
      className={`absolute inset-x-0 bottom-0 z-20 flex flex-col rounded-t-[24px] border-t border-[#e5e7eb] bg-white shadow-[0_-4px_12px_rgba(0,0,0,0.1)] ${
        dragHeight === null ? 'transition-[height] duration-200' : ''
      }`}
      style={{ height }}
    >
      <div
        className="shrink-0 touch-none select-none"
        onPointerDown={onPointerDown}
      >
        <div className="flex justify-center pt-3 pb-1">
          <button
            type="button"
            onClick={() => onSnapChange(snap === 'full' ? 'collapsed' : SNAP_ORDER[SNAP_ORDER.indexOf(snap) + 1])}
            onKeyDown={onKeyDown}
            aria-label={`목록 높이 조절, 현재 ${SNAP_LABEL[snap]}. 위·아래 화살표로 바꿉니다`}
            className="flex h-6 w-16 items-center justify-center rounded-full focus-visible:outline-2 focus-visible:outline-brand"
          >
            <span className="block h-1 w-10 rounded-full bg-[#d1d5dc]" />
          </button>
        </div>
        <div className="flex items-center justify-between px-4 pb-1">
          <div className="text-[13px] font-bold leading-[19.5px] text-[#101828]">{title}</div>
          <button
            type="button"
            onClick={() => onSnapChange(snap === 'collapsed' ? 'half' : 'collapsed')}
            aria-label={snap === 'collapsed' ? '목록 펼치기' : '목록 접기'}
            aria-expanded={snap !== 'collapsed'}
            className="relative flex size-6 items-center justify-center text-[#99a1af] before:absolute before:-inset-2.5 before:content-['']"
          >
            <ChevronDownIcon className={`size-4 transition-transform ${snap === 'collapsed' ? 'rotate-180' : ''}`} />
          </button>
        </div>
      </div>
      <div className={`min-h-0 flex-1 ${snap === 'collapsed' && dragHeight === null ? 'invisible' : ''}`}>{children}</div>
    </section>
  );
}
