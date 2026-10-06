// 동작 보존 테스트 — 문항 은행(item-bank)의 문항 검색 화면 search.php
//
// 케이스 설계 근거: docs/item-bank/BUSINESS-RULES.md 의 BR-01 ~ BR-30.
// 값은 시드(db/mariadb/init/02-seed.sql)에 맞췄다 — 단원 M5-1 ~ M6-2, 태그 '계산' 등,
// 공개(A) 이면서 난이도 5 미만 문항은 정확히 20건(한 페이지).
//
// 규칙
// - 대상 주소는 lib/target.mjs 가 정한다(환경 변수 TARGET_BASE_URL, 없으면 레거시 기본 주소).
// - 응답은 fetchNormalized 로 {status, rows, count, message} 모양으로 바꾼 뒤 스냅샷과 비교한다.
// - 기대값을 손으로 적지 않는다. 지금 레거시의 실제 응답이 기대값이다(npm run baseline -- item-bank).
// - 레거시와 새 API 양쪽에서 같은 결과가 나와야 하므로 건너뛰기를 두지 않는다.
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';
const PATH = '/search.php';

async function search(params) {
  return fetchNormalized(MODULE, PATH, params);
}

describe('item-bank · 문항 검색(search.php)', () => {
  // ── 정상 입력 ───────────────────────────────────────────────
  it('정상 — 조건 없는 기본 검색: 공개만 · 난이도 5 제외 · 기본 정렬 · 20건 (BR-01 BR-10 BR-17 BR-25)', async () => {
    expect(await search({})).toMatchSnapshot();
  });

  it('정상 — 키워드 · 단원 · 난이도 · 태그 AND 조합 (BR-02 BR-04 BR-08 BR-11 BR-13)', async () => {
    expect(await search({ q: '분수', unit: 'M5-1', level: '1', tag: '계산' })).toMatchSnapshot();
  });

  it('정상 — 단원 정렬 내림차순: 2차 level DESC · 3차 id ASC 고정 (BR-21 BR-22 BR-23)', async () => {
    expect(await search({ sort: 'unit', dir: 'desc' })).toMatchSnapshot();
  });

  // ── 경계값 ─────────────────────────────────────────────────
  it('경계 — 난이도 상한 5 명시: 난이도 5 가 나오는 유일한 경로 (BR-10 BR-11)', async () => {
    expect(await search({ level: '5' })).toMatchSnapshot();
  });

  it('경계 — 난이도 범위 바로 밖 6 (BR-12)', async () => {
    expect(await search({ level: '6' })).toMatchSnapshot();
  });

  it('경계 — 키워드 101자(한글): 100자로 잘림 (BR-05)', async () => {
    expect(await search({ q: '가'.repeat(100) + '나' })).toMatchSnapshot();
  });

  it('경계 — 태그 51자: 50자로 잘린 뒤 미등록 태그 (BR-14 BR-16)', async () => {
    expect(await search({ tag: '계산' + 'x'.repeat(49) })).toMatchSnapshot();
  });

  it('경계 — 마지막 페이지 다음 page=2: 건수는 있고 행은 없음 (BR-25 BR-28 BR-29)', async () => {
    expect(await search({ page: '2' })).toMatchSnapshot();
  });

  it('경계 — 페이지 상한 초과 page=1000: 999 로 고정 (BR-27)', async () => {
    expect(await search({ page: '1000' })).toMatchSnapshot();
  });

  // ── 빈 값 · 누락 ────────────────────────────────────────────
  it('빈 값 — 모든 파라미터 빈 문자열: 조건 없음과 같은지 (BR-04 BR-10 BR-17 BR-26)', async () => {
    expect(
      await search({ q: '', unit: '', level: '', tag: '', sort: '', dir: '', page: '' }),
    ).toMatchSnapshot();
  });

  it('빈 값 — 공백만 있는 키워드 · 난이도 · 단원: trim 후 빈 값 (BR-04 BR-08 BR-10)', async () => {
    expect(await search({ q: '  ', level: ' ', unit: ' ' })).toMatchSnapshot();
  });

  // ── 이상한 값 ───────────────────────────────────────────────
  it('이상값 — 음수 페이지 page=-1: 1페이지로 (BR-26)', async () => {
    expect(await search({ page: '-1' })).toMatchSnapshot();
  });

  it('이상값 — 형식은 맞지만 없는 단원 코드 Z99-99: 거부 없이 조회 (BR-09)', async () => {
    expect(await search({ unit: 'Z99-99' })).toMatchSnapshot();
  });

  it('이상값 — 아주 긴 키워드 1000자(ASCII): 100자로 잘림 (BR-04 BR-05)', async () => {
    expect(await search({ q: 'a'.repeat(1000) })).toMatchSnapshot();
  });

  it('이상값 — 숫자가 섞인 난이도 3a: (int) 변환으로 난이도 3 조회 (BR-12)', async () => {
    expect(await search({ level: '3a' })).toMatchSnapshot();
  });

  it('이상값 — 대소문자 · 공백 섞인 정렬 기준과 잘못된 방향 (BR-18 BR-19 BR-20 BR-21)', async () => {
    expect(await search({ sort: ' LEVEL ', dir: 'UP' })).toMatchSnapshot();
  });
});
