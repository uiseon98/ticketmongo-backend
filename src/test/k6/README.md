## 1. k6 설치

```powershell
choco install k6 -y
```

---

## 2. `application.yml` 설정

- 테스트 설계에 맞춰 이 값들을 조절하시면 됩니다.

```yaml
app:
  queue:
    max-active-users: 1 # 예매 페이지에 동시 진입 가능한 최대 사용자 수
    access-key-ttl-seconds: 10 # 예매 페이지 접근 키의 유효시간 (단위: 초)
    top-ranker-count: 5 # 최상위 대기자 기준 설정(순위 업데이트 범위)
```

---

## 3. `tests/k6/register-test.js`

- SharedArray length 를 100 으로 늘려서 100명의 가짜 유저를 생성
- per-vu-iterations 실행기로 각 VU당 1회 실행
- 이미 1~100까지 생성되었습니다.
- 실행하려면 DB 체크 후 중복 피해서 설정해주세요.

```js
const users = new SharedArray("reg users", () =>
  Array.from({ length: 100 }, (_, i) => {
    const idx = i + 1;
    //...
  })
);
```

### 실행

```bash
k6 run tests/k6/register-test.js
```

---

## 4. `tests/k6/queue-test.js`

- vus 를 5로 고정하여, DB에 미리 만들어둔 5개의 k6 테스트 계정만 사용
- 필요에 따라 iterations 또는 duration 을 추가로 설정하세요

```js
export const options = {
  vus: 5, // 5명의 VU
  iterations: 1, // 각 VU당 1회
};
```

### 실행

```bash
k6 run tests/k6/queue-test.js
```
