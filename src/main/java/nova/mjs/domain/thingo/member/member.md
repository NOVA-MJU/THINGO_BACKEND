# member 모듈 README

---
## 1. 설계 철학 

| 원칙 | 적용 이유                                                                              |
|------|------------------------------------------------------------------------------------|
| **CQRS (Command / Query 분리)** | *읽기* 로직과 *쓰기* 로직의 의존도를 분리하여<br>① 코드 가독성 및 재사용성 향상 ② 트랜잭션 경계를 명확히 ③ 성능 최적화(읽기 캐시 등) |
| **도메인 기반 패키징** | 기능별가 아닌 **도메인별**로 묶어 변경 영향 범위를 최소화                                                 |
| **인터페이스 우선 설계** | 외부 모듈이 `MemberQueryService` 만 의존하도록 해, 내부 구현 교체가 자유로움                              |
| **관심사 분리(SRP)** | 컨트롤러 ↔ 서비스 ↔ 리포지토리 간 책임을 엄격히 구분                                                    |
| **KISS & Self-Describing** | 팀원이 폴더만 봐도 역할이 드러나도록 네이밍·구조를 단순화                                                   |

---

## 📂 폴더 구조와 역할

| 경로                   | 목적                                  |
| -------------------- | ----------------------------------- |
| **controller/**      | HTTP API 엔드포인트 (회원 CRUD·프로필·이메일 인증) |
| **DTO/**             | 요청·응답용 DTO 모음 (Bean Validation 포함)  |
| **email/**           | 이메일 인증 전용 컨트롤러·서비스                  |
| **entity/**          | JPA 엔티티 및 enum                      |
| **exception/**       | 회원 도메인 전용 예외                        |
| **repository/**      | Spring Data JPA                     |
| **service/query/**   | **조회** 전용 서비스                       |
| **service/command/** | **생성·수정·삭제** 전용 서비스                 |

**계층 흐름** : DTO ▶ controller ▶ service ▶ repository

> **왜 이렇게?**
> • **Query / Command 분리**로 가독성·재사용성 확보
> • 외부 모듈은 `MemberQueryService`만 의존 → 중복 코드 최소화

---

## 🔁 다른 모듈에서 회원 조회하기

```java
nickname = memberQueryService.getMemberByUuid(userUuid).getNickname();
```

`memberQueryService` 만 주입해 호출하면 됩니다.

---

## 🛠️ 회원가입 사용 순서

1. **이메일 중복 검사** `GET /members/email/duplicate`
2. **이메일 인증코드 발송** `POST /member/email/verify`
3. **이메일 인증코드 검증** `POST /member/email/check`
4. **닉네임 중복 검사** `GET /members/nickname/duplicate`
5. **프로필 이미지 업로드** (선택) `POST /members/profile`
   ↳ 응답받은 `profileImageUrl` 사용
6. **최종 회원가입** `POST /members`
   • DTO: `MemberDTO.MemberRegistrationRequestDTO`
   • 필수: 이름·이메일·비밀번호·닉네임·학과·단과대·학번
   • 비밀번호는 BCrypt 해시 저장

---


## 📜 DepartmentName enum (영문 ↔ 한글)

| 코드                                | 한글          |
| --------------------------------- | ----------- |
| HUMANITIES\_COLLEGE               | 인문대         |
| CHINESE\_LITERATURE               | 중어중문학과      |
| JAPANESE\_LITERATURE              | 일어일문학과      |
| ARABIC\_STUDIES                   | 아랍지역학과      |
| KOREAN\_STUDIES                   | 글로벌한국어학과    |
| CREATIVE\_WRITING                 | 문예창작학과      |
| KOREAN\_LITERATURE                | 국어국문학과      |
| ENGLISH\_LITERATURE               | 영어영문학과      |
| ART\_HISTORY                      | 미술사·역사학과    |
| LIBRARY\_SCIENCE                  | 문헌정보학과      |
| CULTURAL\_CONTENT\_STUDIES        | 글로벌문화콘텐츠학   |
| PHILOSOPHY                        | 철학과         |
| SOCIAL\_SCIENCES                  | 사회과학대       |
| PUBLIC\_ADMINISTRATION            | 행정학과        |
| POLITICAL\_DIPLOMACY              | 정치외교학과      |
| LAW                               | 법학과         |
| ECONOMICS                         | 경제학과        |
| INTERNATIONAL\_TRADE              | 국제통상학전공     |
| APPLIED\_STATISTICS               | 응용통계학과      |
| BUSINESS                          | 경영대         |
| BUSINESS\_ADMINISTRATION          | 경영학과        |
| GLOBAL\_BUSINESS\_STUDIES         | 글로벌비즈니스학과   |
| MANAGEMENT\_INFORMATION\_SYSTEMS  | 경영정보학과      |
| MEDIA\_HUMANLIFE                  | 미휴라대        |
| DIGITAL\_MEDIA\_STUDIES           | 디지털미디어학부    |
| YOUTH\_GUIDANCE\_STUDIES          | 청소년지도학과     |
| CHILD\_STUDIES                    | 아동학과        |
| AI\_SOFTWARE                      | AI·소프트웨어대   |
| CONVERGENT\_SOFTWARE\_STUDIES     | 융합소프트웨어학부   |
| DIGITAL\_CONTENT\_DESIGN\_STUDIES | 디지털콘텐츠디자인학과 |
| DATA\_SCIENCE                     | 데이터사이언스학과   |
| APPLICATION\_SOFTWARE             | 응용소프트웨어학과   |
| FUTURE\_CONVERGENCE               | 미래융합대       |
| HONOR                             | 아너칼리지       |
| OTHER                             | 기타          |

> Enum은 대문자로 입력해주셔야합니다. 
