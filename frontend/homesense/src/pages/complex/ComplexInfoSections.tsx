import { useId, useState, type ReactNode } from 'react';
import { BuildingIcon } from '../../components/icons/BuildingIcon';
import { CalendarIcon } from '../../components/icons/CalendarIcon';
import { CarIcon } from '../../components/icons/CarIcon';
import { ChevronDownIcon } from '../../components/icons/ChevronDownIcon';
import { InfoCircleIcon } from '../../components/icons/InfoCircleIcon';
import { LayersIcon } from '../../components/icons/LayersIcon';
import { UsersIcon } from '../../components/icons/UsersIcon';
import { WrenchIcon } from '../../components/icons/WrenchIcon';
import { EmptyState } from '../../components/ui/EmptyState';
import type { ComplexBasicInfo, ComplexExtendedInfo } from '../../features/complex/types';
import { buildDetailGroups, NO_INFO } from './detailGroups';
import { CARD_CLASS, CARD_SHELL_CLASS, formatYearMonth } from './detailFormat';

function countWith(value: number | null | undefined, unit: string): string | null {
  return value == null ? null : `${value.toLocaleString('ko-KR')}${unit}`;
}

/**
 * 시공사. 필드 이름이 `constructor`라 응답에서 키가 빠지면(null, non_null 직렬화) 객체의 상속 속성
 * Object.prototype.constructor(함수)가 읽힌다 — 자기 속성이고 문자열일 때만 값으로 본다.
 */
function constructorName(basic: ComplexBasicInfo): string | null {
  const value: unknown = Object.hasOwn(basic, 'constructor') ? basic.constructor : null;
  return typeof value === 'string' && value.trim() !== '' ? value : null;
}

/**
 * DTL-01 구성요소 3 — 기본정보 요약(Figma 4:3082). 태블릿·데스크톱 3열, 모바일 2열. 값이 없으면 "정보 없음".
 * 모바일 순서는 Figma(24:11738)처럼 행 단위로 채워진다(세대수·동수 / 사용승인일·시공사 / 총주차대수·최고층수).
 */
export function BasicInfoSummary({ basic }: { basic: ComplexBasicInfo }) {
  const items: { label: string; value: string | null; icon: ReactNode }[] = [
    { label: '세대수', value: countWith(basic.householdCount, '세대'), icon: <UsersIcon /> },
    { label: '동수', value: countWith(basic.buildingCount, '개동'), icon: <BuildingIcon /> },
    {
      label: '사용승인일',
      value: basic.approvalDate ? formatYearMonth(basic.approvalDate) : null,
      icon: <CalendarIcon />,
    },
    { label: '시공사', value: constructorName(basic), icon: <WrenchIcon /> },
    { label: '총주차대수', value: countWith(basic.totalParkingCount, '대'), icon: <CarIcon /> },
    { label: '최고층수', value: countWith(basic.highestFloor, '층'), icon: <LayersIcon /> },
  ];
  return (
    <section aria-labelledby="basic-info-title" className={CARD_CLASS}>
      <h2 id="basic-info-title" className="text-[13px] font-bold leading-[19.5px] tracking-[0.65px] text-[#6a7282]">
        단지 기본정보 요약
      </h2>
      <dl className="grid grid-cols-2 gap-4 pt-4 md:grid-cols-3">
        {items.map((item) => (
          <div key={item.label} className="flex min-w-0 items-center gap-3">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-[14px] bg-[#e8f2f0] text-brand">
              {item.icon}
            </span>
            <div className="min-w-0">
              <dt className="text-[11px] font-medium leading-[16.5px] text-[#99a1af]">{item.label}</dt>
              <dd
                className={`break-words text-[14px] font-bold leading-[21px] ${item.value ? 'text-[#101828]' : 'text-[#99a1af]'}`}
              >
                {item.value ?? NO_INFO}
              </dd>
            </div>
          </div>
        ))}
      </dl>
    </section>
  );
}

/**
 * DTL-01 구성요소 4 — 상세정보 토글(Figma 접힘 4:3171, 펼침 6:3753). 별도 API 없이 상세 응답의 extendedInfo로
 * 펼침/접음만 한다. 6그룹(지성 확정), 값이 모두 없는 그룹은 숨긴다. 태블릿·데스크톱 2열, 모바일 1열.
 */
export function DetailInfoSection({ extended, basic }: { extended: ComplexExtendedInfo; basic: ComplexBasicInfo }) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const groups = buildDetailGroups(extended, basic);

  return (
    <section aria-label="상세정보" className={`${CARD_SHELL_CLASS} overflow-hidden`}>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((value) => !value)}
        className="flex w-full items-center justify-between gap-3 px-5 py-4 text-left md:px-6"
      >
        <span className="flex items-center gap-2">
          <ChevronDownIcon className={`size-4 shrink-0 text-[#99a1af] ${open ? 'rotate-180' : ''}`} />
          <span className="text-[14px] font-bold leading-[21px] text-[#101828]">{open ? '상세정보 접기' : '상세정보 보기'}</span>
        </span>
        {!open && (
          <span className="hidden text-right text-[12px] font-medium leading-[18px] text-[#99a1af] md:block">
            관리방식 · 승강기 · 주차/전기차 · 보안/편의시설 등
          </span>
        )}
      </button>
      <div id={panelId} hidden={!open} className="border-t border-[#f3f4f6] px-5 pb-5 md:px-6 md:pb-6">
        {groups.length === 0 ? (
          <p className="pt-5 text-[13px] text-[#99a1af]">등록된 상세정보가 없습니다.</p>
        ) : (
          <div className="grid gap-6 pt-5 md:grid-cols-2 md:gap-x-10">
            {groups.map((group) => (
              <div key={group.title} className="min-w-0">
                <h3 className="border-b border-[#f3f4f6] pb-2 text-[11px] font-bold leading-[16.5px] tracking-[0.55px] text-brand">
                  {group.title}
                </h3>
                <dl className="space-y-2 pt-3">
                  {group.rows.map((row) => (
                    <div key={row.label} className="flex items-start gap-4 text-[12.5px] leading-[18.75px]">
                      <dt className="w-[120px] shrink-0 text-[#6a7282]">{row.label}</dt>
                      <dd
                        className={`min-w-0 flex-1 whitespace-pre-line break-words text-right ${
                          row.value ? 'font-semibold text-[#1e2939]' : 'text-[#99a1af]'
                        }`}
                      >
                        {row.value ?? NO_INFO}
                      </dd>
                    </div>
                  ))}
                </dl>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

/** 매칭 대기 단지 — 기본정보·상세정보 대신 안내(UI정의서 7.3절). 실거래 이력·가격 추이는 그대로 보인다. */
export function MatchPendingNotice() {
  return (
    <section className={CARD_CLASS} aria-label="단지 정보 준비 중">
      <EmptyState
        icon={<InfoCircleIcon className="size-6" />}
        title="단지 정보를 준비 중입니다"
        description="이 단지의 기본정보는 아직 연결되지 않았습니다. 실거래 이력은 아래에서 확인할 수 있습니다."
      />
    </section>
  );
}
