# 학생회(ADMIN) 계정 등록 및 로그인 플로우 문서

## 📈 개요

MJS 관리자 페이지는 일반 사용자와 다른 인증 및 권한 체계를 따릅니다.
해당 문서는 학생회 관리자(ADMIN) 계정의 회원가입 및 로그인, 정보 수정, 검증 저체에 대해 정리한 명세서입니다.

---

## ✅ 1. 기본 로그인 프로세스

학생회 관리자도 로그인 창 자체는 일반 사용자와 동일합니다.
하지만 로그인 이후에는 Member.role 값에 따라 리디렉션 경로가 달리지는 구조입니다.

| 구분      | 리디렉션 경로    | 조건           |
| ------- | ---------- | ------------ |
| 일반 사용자  | 메인 서비스 페이지 | role = USER  |
| 학생회 관리자 | 관리자 전용 페이지 | role = ADMIN |

* 로그인 시 이메일 주소는 **mjs 치열에서 발급한 가상 계정** (`id@mju.ac.kr`) 을 사용합니다.
* 해당 계정은 실제 이메일이 아닐 수 있는 **username** 값입니다.

---

## 🏗️ 2. 관리자 계정 등록 절차 (API)

### ✨ 전제: OPERATOR가 첫 관리자 계정을 등록

관리자 계정은 직접 회원가입을 통해 생성하는 것이 아니라, 사전 컨텍 등을 통해 필수 정보를 수집한 후 OPERATOR가 정보를 기본으로 등록합니다.

| 필드명            | 설명                                                              |
| -------------- | --------------------------------------------------------------- |
| email          | 가상 로그인 이메일 (e.g. [dsadmin@mju.ac.kr](mailto:dsadmin@mju.ac.kr)) |
| name           | 학과 학생회 이름                                                       |
| contactEmail   | 실제 연락 가능한 이메일                                                   |
| departmentName | 소속 학과 (Enum)                                                    |
| password       | `hellomjs1!` 로 자동 설정                                            |
| role           | ADMIN (자동 설정)                                                   |
| studentNumber  | null                                                            |

> 🔐 비밀번호는 `hellomjs1!`로 통일 설정됩니다. 후에 비밀번호 변경을 해야 합니다.

---

## 🔐 3. 비밀번호 변경 호로우 (contactEmail 인증)

1. "비밀번호 변경" 선택
2. 입력한 이메일 = contactEmail 값 검증
3. contactEmail로 인증 이메일 및 인증번호 보내기
4. 인증 성공 시, 비밀번호 변경 가능

> ⚠️ 인증에 사용되는 이메일은 email이 아니라 **contactEmail**입니다.

---

## 📩 4. 이메일 검증 API

* 학생회 회원가입 페이지에서 이메일 입력 → \[검증] 버튼 클릭
* 실제 이메일 발송이 아닌, 이 이메일이 서버에 등록된 관리자 계정인지 확인
* 검증 통과 시 회원가입 다음 단계로 진행 가능

---

## 🔄 5. 관리자 정보 수정 API

### 요청 필드 (StudentCouncilUpdateDTO)

| 필드명             | 필요 | 설명               |
| --------------- | -- | ---------------- |
| email           | ✅  | 가상 이메일           |
| name            | ✅  | 관리자 이름 (학생회)     |
| password        | ✅  | 새 비밀번호           |
| departmentName  | ✅  | 학과               |
| college         | ✅  | 단과대학             |
| profileImageUrl |    | CloudFront URL 등 |
| slogan          |    | 학생회 쉐타리          |
| description     |    | 학과 소개            |
| instagramUrl    |    | SNS 주소           |
| homepageUrl     |    | 공식 페이지           |

### 예시

```json
{
  "email": "mjsearch@mju.ac.kr",
  "name": "데이터사이언스 학생회",
  "password": "newSecurePw1!",
  "college": "AI_SOFTWARE",
  "departmentName": "DATA_SCIENCE",
  "profileImageUrl": "https://cdn.thingo.kr/profiles/admin.jpeg",
  "slogan": "함께, 바꾸다!",
  "description": "명지대학교 학생들을 위한 대표 조직입니다.",
  "instagramUrl": "https://instagram.com/mju_student_council",
  "homepageUrl": "https://student.mju.ac.kr"
}
```

---

## 🗎 6. 기탅 예외 상황

| 상황       | HTTP Status | 예외 메시지                  |
| -------- | ----------- | ----------------------- |
| 필수 필드 누락 | 400         | VALIDATION\_FAILED      |
| 권한 부족    | 403         | ACCESS\_DENIED          |
| 서버 오류    | 500         | INTERNAL\_SERVER\_ERROR |

---

## 📌 권한 구조 요약

| 역할       | 설명                    |
| -------- | --------------------- |
| OPERATOR | 시스템 관리자. 관리자 계정 등록 가능 |
| ADMIN    | 학생회 관리자               |
| USER     | 일반 사용자                |


