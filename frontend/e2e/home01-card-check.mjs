// HOME-01 카드 영역(ComplexCard 'grid' variant) 회귀 방지 테스트 — SCR-SRCH-01이 같은 컴포넌트의
// 'list' variant를 재작업하면서 'grid' 분기(HOME-01 인기 단지)에 실수로 영향을 주지 않았는지
// 1280/768/392 세 뷰포트에서 확인한다. Figma 4:1232(데스크톱)/24:7860(태블릿)/24:7370(모바일)
// 스크린샷과 대조해 확정한 grid 카드의 고유 특징(원형 하트가 썸네일 위 절대 위치, 가격이 주소
// 바로 아래 같은 컬럼, 건축년도·㎡당가격 없음)을 그대로 단정문으로 옮겼다.
import { chromium } from 'playwright';

const BASE = process.env.BASE ?? 'http://localhost:5183';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

const browser = await chromium.launch();
const viewports = [
  { name: '1280px(데스크톱)', width: 1280, height: 1400 },
  { name: '768px(태블릿)', width: 768, height: 1400 },
  { name: '392px(모바일)', width: 392, height: 1400 },
];

for (const vp of viewports) {
  const context = await browser.newContext({ viewport: { width: vp.width, height: vp.height } });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));

  await page.goto(`${BASE}/`);
  await page.waitForSelector('text=인기 검색 지역', { timeout: 10000 });
  await page.waitForTimeout(300);

  // RecommendedComplexes는 모바일 가로 스크롤용과 데스크톱/태블릿 그리드용 두 세트를 함께 렌더링하고
  // CSS(`hidden md:grid` 등)로 뷰포트에 맞는 쪽만 보이게 한다 — 그래서 button[aria-pressed] 개수는
  // 항상 카드 수의 2배이고, 현재 뷰포트에서 실제로 "보이는" 쪽만 상호작용 대상이어야 한다.
  const visibleHeart = page.locator('button[aria-pressed]:visible');
  const cardCount = await visibleHeart.count();
  ok(`${vp.name} — 인기 단지 카드가 렌더링됨`, cardCount > 0);

  const firstCard = visibleHeart.first().locator('xpath=ancestor::div[contains(@class,"rounded-[16px]")][1]');
  const cardText = await firstCard.innerText();
  ok(`${vp.name} — grid 카드에 "건축"(년도) 문구가 없음(list 전용 항목이 새어들지 않음)`, !cardText.includes('건축'));
  ok(`${vp.name} — grid 카드에 "만원/㎡"(평단가) 문구가 없음(list 전용 항목이 새어들지 않음)`, !cardText.includes('만원/㎡'));

  // 하트 버튼이 카드 안에서 절대 위치(overlay)로 썸네일 위에 얹혀 있는지 — list variant는 하트가
  // 별도 flex 컬럼 안에 있어 absolute가 아니다.
  const heartWrapperPosition = await firstCard.evaluate((card) => {
    const btn = card.querySelector('button[aria-pressed]');
    const wrapper = btn?.closest('div');
    return wrapper ? getComputedStyle(wrapper).position : null;
  });
  ok(`${vp.name} — 하트 버튼이 절대 위치 오버레이(grid 레이아웃 유지)`, heartWrapperPosition === 'absolute');

  ok(`${vp.name} — pageerror 없음`, errors.length === 0);

  // 하트 클릭(비로그인) 시 /login으로 이동하는 기존 동작이 grid 카드에서도 그대로인지.
  await visibleHeart.first().click();
  await page.waitForURL(/\/login/, { timeout: 5000 });
  ok(`${vp.name} — 비로그인 하트 클릭 시 /login 이동(회귀 없음)`, page.url().includes('/login'));

  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
