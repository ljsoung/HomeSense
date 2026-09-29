import { chromium } from 'playwright';

import { BASE } from './base.mjs';
let pass = 0;
let fail = 0;
function ok(name, cond) { if (cond) { pass++; console.log(`PASS ${name}`); } else { fail++; console.log(`FAIL ${name}`); } }

const browser = await chromium.launch();

// 1) 슬라이더 하한 경계 — 상한(건축년도)만 옮겼을 때 buildYearMin이 요청에 전혀 없는지 네트워크로 확인
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  const requests = [];
  page.on('request', (r) => {
    if (r.url().includes('/api/complexes/search')) requests.push(r.url());
  });
  await page.goto(`${BASE}/search?regionCode=4100000000&regionLabel=%EA%B2%BD%EA%B8%B0%EB%8F%84`);
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });

  // 건축년도 슬라이더의 "최대값 직접 입력" 숫자 필드만 바꾼다(최소값은 절대 건드리지 않음).
  const buildYearMaxInput = page.getByLabel('건축년도 최대값 직접 입력');
  await buildYearMaxInput.fill('2010');
  await buildYearMaxInput.blur();
  await page.getByRole('button', { name: '필터 적용' }).click();
  await page.waitForTimeout(700);

  const lastReq = requests[requests.length - 1] ?? '';
  ok('상한만 옮겨도 buildYearMin 파라미터가 요청에 없음(생략)', !lastReq.includes('buildYearMin='));
  ok('buildYearMax=2010은 요청에 포함됨', lastReq.includes('buildYearMax=2010'));

  // 같은 원리로 면적/금액도 하한을 건드리지 않고 상한만 바꿔 하한이 생략되는지 확인.
  requests.length = 0;
  const areaMaxInput = page.getByLabel('전용면적 최대값 직접 입력');
  await areaMaxInput.fill('150');
  await areaMaxInput.blur();
  const amountMaxInput = page.getByLabel('거래금액 최대값 직접 입력');
  await amountMaxInput.fill('100000');
  await amountMaxInput.blur();
  await page.getByRole('button', { name: '필터 적용' }).click();
  await page.waitForTimeout(700);
  const lastReq2 = requests[requests.length - 1] ?? '';
  ok('전용면적 상한만 옮겨도 areaMin 생략', !lastReq2.includes('areaMin='));
  ok('거래금액 상한만 옮겨도 amountMin 생략', !lastReq2.includes('amountMin='));
  ok('areaMax/amountMax는 요청에 포함됨', lastReq2.includes('areaMax=150') && lastReq2.includes('amountMax=100000'));

  await context.close();
}

// 2) 1968~69년 준공 단지가 buildYearMin 생략(필터 없음) 상태에서 실제로 포함되는지 — DB 실측과 대조
//    (백엔드가 buildYearMin 파라미터 부재 시 하한 필터를 아예 안 거는지는 0단계에서 이미 확인했으나,
//    이번엔 실제 응답에 1990년 이전 준공 단지가 살아있는지 직접 확인한다)
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/search?regionCode=4100000000&regionLabel=%EA%B2%BD%EA%B8%B0%EB%8F%84&sort=AMOUNT`);
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
  const bodyText = await page.locator('body').innerText();
  ok('기본(필터 없음) 상태에서 1990년 이전 준공 단지 문구가 실제로 존재(하한 미적용 방증)', /건축 19[678]\d년/.test(bodyText));
  await context.close();
}

// 3) 월세 카드 표시 — "보증금 X · 월세 Y만원" + ㎡당 가격 없음 + 슬라이더 라벨 "보증금"
{
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/search?regionCode=4111100000&regionLabel=test`);
  await page.waitForSelector('a[href^="/complexes/"]', { timeout: 10000 });
  await page.getByRole('radio', { name: '월세' }).check();
  ok('월세 선택 시 슬라이더 라벨이 "보증금"으로 바뀜', await page.getByText('보증금', { exact: true }).isVisible());
  await page.getByRole('button', { name: '필터 적용' }).click();
  await page.waitForTimeout(700);

  const cards = await page.locator('a[href^="/complexes/"]').count();
  if (cards > 0) {
    const firstCardText = await page.locator('a[href^="/complexes/"]').first().innerText();
    ok('월세 카드에 "보증금"과 "월세" 문구가 함께 표시', firstCardText.includes('보증금') && firstCardText.includes('월세'));
    ok('월세 카드에 ㎡당 가격 표기 없음(만원/㎡ 없음)', !firstCardText.includes('만원/㎡'));
  } else {
    console.log('SKIP 월세 카드 표시 확인 — 수원장안구에 월세 매물 0건(다른 지역으로 재확인 필요)');
  }
  await context.close();
}

await browser.close();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
