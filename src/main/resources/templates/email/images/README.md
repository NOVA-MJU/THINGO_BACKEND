# 인증 메일 인라인 이미지

`verification-code.html` 템플릿이 `cid:`로 참조하는 이미지를 이 디렉토리에 둔다.
EmailService 가 발송 시 CID 인라인으로 첨부하므로 **외부 호스팅이 필요 없다**.

필요한 파일 (파일명 정확히 일치해야 함):

| 파일명 | 용도 | 권장 크기 |
| --- | --- | --- |
| `th_logo.png` | 상단 Th 로고 | 가로 130px 내외 (표시 65px @2x) |
| `thingo_logo.png` | 하단 Thingo 워드마크 | 가로 230px 내외 (표시 115px @2x) |
| `instagram.png` | 푸터 인스타그램 아이콘 | 68x68px (표시 34px @2x) |

파일이 없으면 발송은 정상 진행되며 해당 이미지만 표시되지 않는다(로그 WARN).
cid 매핑은 `EmailService.INLINE_IMAGES` 참조.
