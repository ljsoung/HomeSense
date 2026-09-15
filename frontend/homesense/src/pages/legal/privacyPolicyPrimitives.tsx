import type { ReactNode } from 'react';

/** privacyPolicySections.tsx가 섹션 본문을 조립할 때 쓰는 최소 프레젠테이션 컴포넌트 모음. */

export function P({ children }: { children: ReactNode }) {
  return <p className="text-[13px] leading-[1.75] text-[#364153]">{children}</p>;
}

export function Ul({ children }: { children: ReactNode }) {
  return <ul className="flex list-disc flex-col gap-1.5 pl-5 text-[13px] leading-[1.75] text-[#364153]">{children}</ul>;
}

export function SubHeading({ children }: { children: ReactNode }) {
  return <p className="text-[14px] font-bold text-[#101828]">{children}</p>;
}

export function Note({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-[10px] bg-[#f7f8fa] px-3.5 py-2.5 text-[12px] leading-[1.7] text-[#6a7282]">{children}</div>
  );
}

export function Table({ headers, rows }: { headers: string[]; rows: string[][] }) {
  return (
    <div className="overflow-x-auto rounded-[10px] border border-[#e5e7eb]">
      <table className="w-full min-w-[560px] border-collapse text-[12.5px]">
        <thead>
          <tr className="bg-[#f7f8fa]">
            {headers.map((h) => (
              <th key={h} className="border-b border-[#e5e7eb] px-3 py-2.5 text-left font-semibold text-[#364153]">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i} className="border-b border-[#f3f4f6] last:border-b-0">
              {row.map((cell, j) => (
                <td key={j} className="px-3 py-2.5 align-top text-[#364153]">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
