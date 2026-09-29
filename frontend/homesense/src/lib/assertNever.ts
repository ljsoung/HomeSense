/**
 * exhaustive switch의 default 분기에서 부른다. 유니언에 값이 새로 늘면 그 값이 `never`에 할당되지 않아
 * 컴파일이 실패한다 — 새 상태를 처리하지 않은 분기를 타입 검사로 잡는다.
 */
export function assertNever(value: never): never {
  throw new Error(`Unhandled value: ${JSON.stringify(value)}`);
}
