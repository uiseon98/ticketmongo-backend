## 📦 파일 업로드 시스템 구조
> 📄 이 문서는 Supabase → S3 마이그레이션을 고려한 **공통 파일 업로드 시스템** 구조를 설명합니다.  
> 루트 README에서 오신 경우: [← 메인으로 돌아가기](./README.md)


---
### ✅ 공통 업로더 인터페이스
```java
public interface StorageUploader {
String uploadFile(MultipartFile file, String bucket, String path);
}
```
- Supabase / S3 공통 업로드 방식 추상화
- 추후 S3 마이그레이션 시 `StorageUploader`만 교체하면 기존 코드 그대로 유지 가능


<br>

### 🧱 현재 구현체
|구현체|	설명| 	비고      |
|-|-|----------|
|`SupabaseUploader`|	Supabase Storage SDK 기반 업로드| 	현재 사용 중 |
|`S3Uploader`|	S3 클라이언트 기반 업로드 구현체|	🔒 주석 처리 상태로 미리 구현 완료 (@Profile("s3"))|


<br>

### 🛠️ 의존성
```groovy
// Supabase Storage 연동용 SDK
implementation 'io.supabase:storage-java:1.1.0'

// AWS Spring Cloud S3 연동 (Spring Boot 3.x 이상)
implementation 'io.awspring.cloud:spring-cloud-aws-starter-s3:3.4.0'
```

<br>

### ⚙️ 설정 파일 구조
**🔐 .env.example**
```env
# Supabase
SUPABASE_URL=...
SUPABASE_KEY=...
SUPABASE_PROFILE_BUCKET=...
SUPABASE_POSTER_BUCKET=...
SUPABASE_DOCS_BUCKET=...

# AWS S3 (🔒 주석 처리 상태)
# AWS_ACCESS_KEY_ID=...
# AWS_SECRET_ACCESS_KEY=...
# AWS_REGION=ap-northeast-2
# AWS_S3_BUCKET=ticketmon-prod-assets
# AWS_S3_PROFILE_PREFIX=profile-imgs/
# AWS_S3_POSTER_PREFIX=poster-imgs/
# AWS_S3_DOCS_PREFIX=seller-docs/
```

**🔧 application-dev.yml (예시 / application-prod 참고)**
```yaml
supabase:
url: ${SUPABASE_URL}
key: ${SUPABASE_KEY}
profile-bucket: ${SUPABASE_PROFILE_BUCKET}
poster-bucket: ${SUPABASE_POSTER_BUCKET}
docs-bucket: ${SUPABASE_DOCS_BUCKET}

# 🔒 S3는 마이그레이션 시 사용 예정. 현재는 주석 처리 상태
# cloud:
#   aws:
#     credentials:
#       access-key: ${AWS_ACCESS_KEY_ID}
#       secret-key: ${AWS_SECRET_ACCESS_KEY}
#     region:
#       static: ${AWS_REGION}
#     s3:
#       bucket: ${AWS_S3_BUCKET}
#       profile-prefix: ${AWS_S3_PROFILE_PREFIX}
#       poster-prefix: ${AWS_S3_POSTER_PREFIX}
#       seller-docs-prefix: ${AWS_S3_DOCS_PREFIX}
```

<br>

### 🚀 S3 마이그레이션 전략
|항목|	Supabase 구조|	S3 구조 (계획)|
|-|-|-|
|버킷|	기능별 분리 (3개)|	하나의 버킷 사용|
|구분|	버킷 기준|	prefix(폴더) 기준|
|예시|	ticketmon-dev-profile-imgs|	ticketmon-prod-assets/profile-imgs/{userId}.jpg|

<br>

### 🔄 마이그레이션 시 할 일
1. .env에서 S3 환경변수 활성화
2. application-prod.yml에서 Supabase 설정 주석 처리, S3 설정 주석 해제
3. @Profile("s3")로 S3Uploader 활성화
4. 필요 시 업로드 경로 변경 (prefix 구조 적용)

<br>
<br>
<br>

---
## ✅ 추가 참고 항목
### 1. ✅ 업로더 사용 예시 (서비스에서 어떻게 사용하는지)
```java
@Autowired
private StorageUploader storageUploader;

String imageUrl = storageUploader.uploadFile(file, bucketName, path);
```
> 📌 Supabase/S3 상관없이 이 방식으로 동일하게 사용 가능

<br>

### 2. 🔀 Profile 설정 흐름 요약
```yaml
# application.yml
spring:
profiles:
active: ${SPRING_PROFILES_ACTIVE:prod}  # 기본값은 prod

# application-prod.yml
spring:
profiles:
active: s3  # 또는 supabase

# application-dev.yml
spring:
profiles:
active: supabase
```
> 💡 prod 환경에선 s3 또는 supabase 중 하나를 선택해서 override

<br>

### 3. 📍 파일 경로(path) 설계 규칙 예시
|구분|	설명|	예시|
|-|-|-|
|프로필 이미지|	유저 ID 기반 파일명|	profile-imgs/{userId}.jpg|
|포스터 이미지|	콘서트 ID 기반|	poster-imgs/{concertId}.png|
|판매자 문서|	UUID 기반 문서|	seller-docs/{uuid}.pdf|
> ✏️ path는 서비스 로직에서 결정해서 uploadFile() 호출 시 전달해야 함

<br>

### 4. 📁 파일 크기 / 확장자 제한 (현재 적용 중이라면)
```java
// 예시
if (file.getSize() > MAX_SIZE) {
throw new IllegalArgumentException("파일 크기 제한 초과");
}
```
- ✅ 권장: 2MB 이하
- ✅ 허용 확장자: jpg, png, webp, pdf (필요에 따라)

<br>

### 5. 🧪 로컬 테스트 가이드 (Swagger, Postman 등)
```http
PATCH /api/v1/users/me/profile/image
Content-Type: multipart/form-data

[file] = test.jpg
```
> ⚠️ Swagger에서 multipart 업로드 시 버그 있을 수 있으므로 Postman 추천
