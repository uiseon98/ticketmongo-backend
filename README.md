# AIBE1-FinalProject-Team03

---
# (2025.06.15) 초기 환경 세팅 관련 공지

## 1. 초기 환경 설정 요약 (Security + Swagger)

### ✅ 적용 대상

- `SecurityConfig.java`
- `SwaggerConfig.java`

---

## 🔐 Spring Security 설정 요약

| 구분 | 설명                                                                    |
| --- |-----------------------------------------------------------------------|
| **현재 인증 상태** | **전체 인증 없이 테스트 가능** 상태입니다.→ `anyRequest().permitAll()`                |
| **JWT 필터** | 자리만 확보되어 있으며, **실제 필터는 구현되지 않았습니다.**<br>→ 로그인/회원가입/JWT 담당자가 추후 구현 예정  |
| **권한 설정** | `/admin/**` 등 권한별 URL은 **아직 작동하지 않으며**, 주석으로 표시되어 있습니다.               |
| **인증 예외 응답 처리** | 인증 실패(401), 권한 없음(403)에 대해 커스텀 에러 메시지를 반환하도록 설정됨.                     |
| **로그아웃 설정** | `/logout` 호출 시 세션 무효화 + JWT 쿠키 삭제 설정만 되어있습니다.<br> → JWT 무효화는 실제 구현 필요 |
| **CORS 설정** | 프론트 요청 허용(`localhost:3000`, `localhost:8080`) 및 쿠키/헤더 허용 구성 완료        |

🔧 *📌 현재 인증 설정은 모두 테스트 편의를 위한 임시 상태입니다. 실제 로그인 기능 구현 시 반드시 수정이 필요합니다.*

---

## 📘 Swagger 설정 요약

| 구분 | 설명 |
| --- | --- |
| **접근 경로** | `/swagger-ui/index.html` 에서 UI 확인 가능 |
| **보안 설정** | JWT 사용을 전제로 `Authorize` 버튼이 설정되어 있음 (`bearer` 방식) |
| **실제 인증 작동 여부** | ❌ 현재는 JWT 미연동 상태이므로 `Authorize` 버튼은 **기능하지 않습니다.** |
| **구성 목적** | 이후 JWT 기능 구현 시 Swagger에서도 로그인 후 API 테스트 가능하게 하기 위함입니다. |

---

## 📌 팀원별 작업 참고 사항

| 역할 | 확인해야 할 부분 |
| --- | --- |
| ✅ **로그인/회원가입/JWT 담당자** | - `JwtAuthenticationFilter` 구현 후 SecurityConfig의 주석 해제 <br>- Swagger와 JWT 연동 적용 필요 |
| ✅ **API 개발 담당자** | - 현재는 인증 없이 API 호출 가능하지만, **향후 인증 필요**를 고려한 설계 권장 |
| ✅ **인프라 담당자** | - SecurityConfig, SwaggerConfig 위치와 현재 상태 숙지 <br>- 추후 인증 정책 변경 시 전체 테스트 환경 영향 고려 |
| 🧪 **테스트 시 참고** | - 현재 Swagger에서는 인증 없이 모든 API 호출 가능 <br>- 로그인 후 토큰이 생기면, Swagger Authorize 버튼을 통해 인증 테스트 가능 예정 |

---

## ✍️ 현재 SecurityConfig 주요 코드 상태 (요약)

```java
// 현재 모든 API 인증 없이 허용
.anyRequest().permitAll()

// 향후 인증 설정 전환용 주석
// .anyRequest().authenticated()

// JWT 필터 자리만 확보 (구현 전)
 // http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```


---
## 2. 📛 공통 예외 처리 정책 (Global Exception Handling)
### ✅ 목적
- 예외 응답을 일관된 형식으로 내려주기 위해 설정합니다.
- 에러 코드와 메시지를 한 곳에서 통합 관리함으로써 유지보수를 쉽게 합니다.
- 개발자/사용자 모두에게 명확한 에러 정보를 제공합니다.
  <br>

### 🧩 기본 응답 형식
**❗ 실패(Error) 응답 (`ErrorResponse`)**
```json
{
"success": false,
"status": 400,
"code": "C001",
"message": "유효하지 않은 입력값입니다."
}
```


**✅ 성공(Success) 응답 (`SuccessResponse`)**
```json
{
"success": true,
"message": "조회 성공",
"data": {
"title": "콘서트 A",
"date": "2025-12-01"
}
}
```
<br>

### ⚙️ 전체 구성도
```text
[Controller] → 예외 발생
↓
[BusinessException] 또는 기타 Exception
↓
[GlobalExceptionHandler]에서 처리
↓
ErrorCode에 따라 ErrorResponse 생성
↓
클라이언트에 통일된 에러 응답 반환
```
<br>

### 🧱 주요 구성 요소 설명
|파일명|	역할|
|-|-|
|`ErrorCode.java`|	❗ 전체 에러 코드/메시지 관리 (enum으로 구분)|
|`BusinessException.java`|	직접 정의한 예외(비즈니스 로직 위반)를 처리하는 커스텀 예외 클래스|
|`ErrorResponse.java`|	실패 응답 포맷 클래스 (`success: false`)|
|`SuccessResponse.java`|	성공 응답 포맷 클래스 (`success: true`)|
|`GlobalExceptionHandler.java`|	전역 예외 핸들러 (`@RestControllerAdvice`)|
<br>

### ✏️ 사용 예시
**1. ❗ 비즈니스 예외 발생**
```java
   if (seat.isAlreadyBooked()) {
   throw new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN);
   }
```
→ 클라이언트에 아래처럼 반환됨:

```json
{
"success": false,
"status": 409,
"code": "B003",
"message": "이미 선택된 좌석입니다."
}
```
<br>

**2. ✅ 성공 응답 반환**
```java
   return ResponseEntity.ok(SuccessResponse.of("등록 성공", concertDto));
```
→ 클라이언트에 아래처럼 반환됨:

```json
{
"success": true,
"message": "등록 성공",
"data": {
"concertId": 12,
"title": "Waiting Cha Live"
}
}
```
<br>

### 💡 팀원 참고사항
| 항목                                                    |	설명|
|-------------------------------------------------------|-|
| **에러 코드는 어디에 정의하나요?**                                 |	`ErrorCode.java` enum에 추가하세요|
| **새로운 예외 만들려면?**                                      |	`throw new BusinessException(ErrorCode.원하는_코드);`|
| **공통 에러 메시지는 어디서 바꾸나요?**                              |	`ErrorCode.java`의 `message` 필드에서 수정|
| **예외 직접 잡고 싶을 땐?**                                    |	`@ExceptionHandler(YourException.class)` 추가 가능|
| **응답에 `success`, `message`, `data` 일관되게 나오게 하고 싶다면?** |	반드시 `SuccessResponse`와 `ErrorResponse`를 사용하세요|

---
### + [추가 문서]
📎 [TimeUtil 설명 보기](src/main/java/com/team03/ticketmongo/_global/util/README.md)