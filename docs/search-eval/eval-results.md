# Search eval results (raw, top-5)

- endpoint: `http://localhost:8080/api/v2/search/detail`  order=relevance  topK=10  queries=72
- zero-result: 2 / 72   error: 0
- latency (all): avg 152.2ms
- latency (keyword only): avg 152.6ms  P95 231ms

## Zero-result queries
- Q60 [노이즈] `ㅁㅈㄷ` (total=0)
- Q62 [노이즈] `ㅎㅇ` (total=0)

## Q01  `수강신청`
intent=학사/수강 / form=정확키워드 / total=996 / lat=175ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.50789 | 2025학년도 동계 계절수업 안내(수강신청 및 등록 |
| 2 | notice | general | 0.50191 | 2026학년도 1학기 수강 희망 교과목 사전조사 실시 안내 |
| 3 | notice | general | 0.50185 | 2025학년도 2학기 수강 희망 교과목 사전조사 실시 안내 |
| 4 | notice | general | 0.4881 | X사업단] 2026학년도 AI·Bigdata·ICT 융합전공 학생설명회(2차) 참여 신청 안내 |
| 5 | notice | general | 0.4881 | X사업단] 2026년도 AI·Data Science 워크숍(1차) 신청 안내 |

## Q02  `수강신청 언제 시작해?`
intent=학사/수강 / form=자연어질문 / total=1099 / lat=186ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.53132 | 2025학년도 동계 계절수업 안내(수강신청 및 등록 |
| 2 | notice | general | 0.52302 | 2025학년도 2학기 봉사시간 적립 및 봉사학점 수강신청 및 취득 안내 |
| 3 | notice | general | 0.52302 | 2025학년도 1학기 봉사시간 적립 및 봉사학점 수강신청 및 취득 안내 |
| 4 | notice | general | 0.51607 | 2025학년도 2학기 수강신청 일정 안내 |
| 5 | notice | academic | 0.51133 | 2026학년도 하계 계절수업 안내(수강신청 및 등록) - 2026.5.22.시간표 업데이트 |

## Q03  `수강 신청`
intent=학사/수강 / form=띄어쓰기 / total=996 / lat=211ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.55789 | 2025학년도 동계 계절수업 안내(수강신청 및 등록 |
| 2 | notice | academic | 0.53791 | 2026학년도 하계 계절수업 안내(수강신청 및 등록) - 2026.5.22.시간표 업데이트 |
| 3 | notice | academic | 0.5379 | 2026학년도 하계 계절수업 안내(수강신청 및 등록 |
| 4 | notice | academic | 0.53789 | 2025학년도 하계 계절수업 안내(수강신청 및 등록)[2025.5.30 변동사항 반영 |
| 5 | notice | academic | 0.53789 | 2025학년도 동계 계절수업 안내(수강신청 및 등록 |

## Q04  `졸업요건`
intent=학사/수강 / form=정확키워드 / total=265 / lat=189ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | career | 0.40946 | 인문] 졸업생 특화프로그램 - 졸업(예정)자의 자소서&면접 완성을 위한 '경험정리' 참여자 모집 |
| 2 | notice | career | 0.40941 | MJ대학일자리플러스센터] 졸업(예정)자를 위한 [MJ집중케어센터 |
| 3 | notice | career | 0.40926 | MJ대학일자리플러스센터] 졸업(예정)자를 위한「취준 멘탈관리와 자기이해」참여자 모집 |
| 4 | notice | career | 0.40922 | MJ대학일자리플러스센터] 졸업(예정)자를 위한「2026 채용 면접 대비 컬러 이미지 메이킹」참여자 모집 |
| 5 | notice | career | 0.40922 | 고용노동부] 졸업(예정)자 대상 국민취업지원제도 참여 안내 |

## Q05  `졸업하려면 학점 얼마나 들어?`
intent=학사/수강 / form=자연어질문 / total=388 / lat=228ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.44887 | 2025학년도 후기(2026년 8월) 졸업예정자 학점 이수내역 확인 안내 (조기졸업 예정자 |
| 2 | notice | academic | 0.44887 | 2025학년도 후기(2026년 8월) 졸업예정자 학점 이수내역 확인 안내 (조기졸업 예정자 |
| 3 | notice | academic | 0.44887 | 2025학년도 전기(2026년 2월) 졸업예정자 학점 이수내역 확인 안내 |
| 4 | notice | career | 0.39973 | 인문] 졸업생 특화프로그램 - 졸업(예정)자의 자소서&면접 완성을 위한 '경험정리' 참여자 모집 |
| 5 | notice | career | 0.3997 | MJ대학일자리플러스센터] 졸업(예정)자를 위한 [MJ집중케어센터 |

## Q06  `재수강`
intent=학사/수강 / form=정확키워드 / total=27 / lat=105ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.88897 | 2026학년도 1학기 재수강 처리 신청원서 제출 안내 |
| 2 | notice | academic | 0.88897 | 2026학년도 1학기 재수강 처리 신청원서 제출 안내 |
| 3 | notice | academic | 0.88897 | 2025학년도 동계 계절학기 재수강 처리 신청원서 제출 안내 |
| 4 | notice | academic | 0.88897 | 2025학년도 2학기 재수강 처리 신청원서 제출 안내 |
| 5 | notice | academic | 0.88897 | 2025학년도 1학기 재수강 처리 신청원서 제출 안내 |

## Q07  `계절학기`
intent=학사/수강 / form=정확키워드 / total=581 / lat=129ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.48414 | 신규 노선 추가] 2026학년도 1학기 자연캠퍼스 학기 중 통학·셔틀버스 운행 안내 |
| 2 | notice | general | 0.48414 | 신규 노선 추가] 2026학년도 1학기 자연캠퍼스 학기 중 통학·셔틀버스 운행 안내 |
| 3 | notice | general | 0.48392 | 학기 명지대학교 비전임교원 초빙 공고 |
| 4 | notice | general | 0.48392 | 학기 명지대학교 비전임교원 초빙 공고 |
| 5 | notice | general | 0.48299 | 학기 명지대학교 강사 초빙 공고 |

## Q08  `휴학 신청`
intent=학사/수강 / form=정확키워드 / total=894 / lat=123ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.48718 | 2025학년도 1학기 휴·복학 추가 신청 안내(기간 변경 |
| 2 | notice | general | 0.4871 | 명지대학교 고시원] 2025-2학기 고시장학금 신청 안내 |
| 3 | notice | general | 0.4871 | 학기 고시장학금 신청 안내 |
| 4 | notice | academic | 0.48648 | 2026학년도 1학기 휴·복학 추가 신청 안내 |
| 5 | notice | academic | 0.48648 | 2025학년도 2학기 휴·복학 추가 신청 안내 |

## Q09  `복학`
intent=학사/수강 / form=정확키워드 / total=51 / lat=80ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.8883 | 2026학년도 1학기 휴·복학 신청 안내 |
| 2 | notice | academic | 0.8883 | 2025학년도 2학기 휴·복학 신청 안내 |
| 3 | notice | academic | 0.8883 | 2025학년도 1학기 휴·복학 추가 신청 안내(기간 변경 |
| 4 | notice | academic | 0.88813 | 2026학년도 1학기 휴·복학 추가 신청 안내 |
| 5 | notice | academic | 0.88813 | 2025학년도 2학기 휴·복학 추가 신청 안내 |

## Q10  `전과`
intent=학사/수강 / form=정확키워드 / total=25 / lat=88ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.48914 | 2026학년도 전과(부)[전공변경] 신청 안내_2026.1.6. 전입사정기준 업데이트 |
| 2 | notice | academic | 0.48905 | 2026학년도 전과(부)[전공변경] 허가자 안내문 |
| 3 | notice | academic | 0.489 | 2025학년도 전과(부) 허가자 안내문 |
| 4 | mju_calendar |  | 0.37559 | 재입학 원서접수기간 [학 부] 복수·부·융합전공 접수 [대학원] 전과 및 과정변경 원서 접수기간 |
| 5 | mju_calendar |  | 0.37559 | 복수·부·융합전공 접수 [학부·대학원] 전과 및 재입학 원서접수기간 [대학원] 과정변경 원서 접수기간 |

## Q11  `복수전공`
intent=학사/수강 / form=정확키워드 / total=12 / lat=107ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.23765 | 2026 |
| 2 | notice | academic | 0.2373 | 2025 |
| 3 | notice | academic | 0.23616 | [교직 |
| 4 | notice | academic | 0.23616 | [교직 |
| 5 | notice | academic | 0.23616 | [교직 |

## Q12  `성적정정`
intent=학사/수강 / form=정확키워드 / total=148 / lat=92ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.48632 | 2025학년도 2학기 성적이의(성적정정) 신청 안내 |
| 2 | notice | academic | 0.48632 | 2025학년도 1학기 성적이의(성적정정) 신청 안내 |
| 3 | notice | academic | 0.48632 | 2024학년도 동계 계절수업 성적이의(성적정정) 신청 안내 |
| 4 | notice | academic | 0.48619 | 2025학년도 동계 계절수업 성적이의(성적정정) 신청 안내 |
| 5 | notice | general | 0.4652 | 자연캠퍼스] 2025학년도 1학기 자연선교지원팀 교육조교(1종) 모집 (신청자격 정정 |

## Q13  `수강정정 기간`
intent=학사/수강 / form=정확키워드 / total=794 / lat=119ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.47076 | 2026학년도 1학기 수강 희망 교과목 사전조사 실시 안내 |
| 2 | notice | general | 0.47072 | 2025학년도 2학기 수강 희망 교과목 사전조사 실시 안내 |
| 3 | notice | general | 0.46039 | 2025학년도 1학기 창업동아리 모집 안내 (기간 연장 |
| 4 | notice | general | 0.45912 | 자연캠퍼스] 2025학년도 1학기 자연선교지원팀 교육조교(1종) 모집 (신청자격 정정 |
| 5 | notice | academic | 0.45153 | 2025학년도 2학기 학사과정 학생의 대학원 교과목 수강 안내_추가 접수 |

## Q14  `학사일정`
intent=학사/수강 / form=정확키워드 / total=508 / lat=108ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.46894 | 2025학년도 2학기 수강신청 일정 안내 |
| 2 | notice | general | 0.46857 | 스포츠·예술대학] 2026학년도 대학 U리그(농구, 배구, 축구) 일정 안내 |
| 3 | notice | general | 0.46857 | 스포츠·예술대학] 2026학년도 대학 U리그(농구, 배구, 축구) 일정 안내 |
| 4 | notice | general | 0.4678 | 자연캠퍼스] 학생예비군훈련 일정 연기 안내 |
| 5 | notice | general | 0.46731 | 인문캠퍼스] 전기안전 법정검사 진행으로 인한 정전 일정 안내 |

## Q15  `중간고사 일정`
intent=학사/수강 / form=정확키워드 / total=472 / lat=127ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | community | NOTICE | 0.72342 | 정보 공유] 2026학년도 1학기 중간고사 일정 안내 |
| 2 | notice | general | 0.46916 | 인문] 2025학년도 1학기 명지대학교 인문캠퍼스 영어 중간고사 실시 안내 |
| 3 | notice | general | 0.4691 | 인문캠퍼스] 2026학년도 1학기 영어 중간고사 실시 안내 |
| 4 | notice | general | 0.4691 | 인문캠퍼스] 2026학년도 1학기 영어 중간고사 실시 안내 |
| 5 | notice | general | 0.4691 | 인문캠퍼스] 2025학년도 2학기 영어 중간고사 실시 안내 |

## Q16  `기말고사`
intent=학사/수강 / form=정확키워드 / total=35 / lat=203ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.90749 | 인문캠퍼스] 2025학년도 2학기 기말고사 실시 안내 |
| 2 | notice | general | 0.90749 | 인문] 2025학년도 1학기 명지대학교 인문캠퍼스 영어 기말고사 실시 안내 |
| 3 | notice | general | 0.89559 | 자연캠퍼스] 2025학년도 2학기 기말고사 실시 안내 |
| 4 | notice | general | 0.89559 | 자연] 2025학년도 1학기 영어, 물리학, 미적분학 기말고사 실시 안내 |
| 5 | mju_calendar |  | 0.78616 | 학부·대학원] 공휴일로 인한 수업보강(기말고사) 기간 |

## Q17  `국가장학금`
intent=장학 / form=정확키워드 / total=278 / lat=127ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.48571 | 산학협력단] 산학협력 우수 학생연구자 선정 및 장학금 지원자 모집 |
| 2 | notice | general | 0.48571 | 산학협력단] 산학협력 우수 학생연구자 선정 및 장학금 지원자 모집 |
| 3 | notice | scholarship | 0.4752 | 2026년 2학기 국가장학금 1차 신청 안내 |
| 4 | notice | general | 0.46957 | 경기RISE사업] 2025학년도 2학기 『경기 RISE G7 (BIO 분야) 특성화 장학금』 신청 안내 |
| 5 | notice | general | 0.46957 | 학기 『경기 RISE G7 (AI 분야) 특성화 장학금』 신청 안내 |

## Q18  `국장 신청 언제까지야`
intent=장학 / form=약어 / total=865 / lat=150ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | scholarship | 0.4714 | 2026년 2학기 국가장학금 1차 신청 안내 |
| 2 | notice | general | 0.46453 | X사업단] 2026년도 AI·Data Science 워크숍(1차) 신청 안내 |
| 3 | notice | general | 0.46453 | 관련 취업 및 최신 기술 특강(2차) 참여 신청 안내 |
| 4 | notice | general | 0.46453 | X사업단] 2026학년도 AI·Bigdata·ICT 융합전공 학생설명회(2차) 참여 신청 안내 |
| 5 | notice | general | 0.46453 | Bigdata·ICT 관련 취업 및 최신기술 특강(1차) - 참여 신청 안내 |

## Q19  `교내장학금`
intent=장학 / form=정확키워드 / total=352 / lat=124ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.48588 | 창업교육센터] 2025학년도 교내 창업스토리 PT 경진대회 공고 |
| 2 | notice | general | 0.48588 | 창업교육센터] 2025학년도 교내 창업 아이디어 경진대회 참가 안내 |
| 3 | notice | general | 0.4845 | 2025학년도 2학기 명지 마일리지 장학금 수혜자 선정 안내 |
| 4 | notice | general | 0.4845 | 2025학년도 1학기 명지 마일리지 장학금 수혜자 선정 안내 |
| 5 | notice | general | 0.48441 | 2026학년도 1학기 명지 마일리지 장학금 수혜자 선정 안내 |

## Q20  `성적우수장학금`
intent=장학 / form=정확키워드 / total=402 / lat=128ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.47803 | 학기 『경기 RISE G7 (AI 분야) 특성화 장학금』 신청 안내 |
| 2 | notice | general | 0.47797 | 산학협력단] 산학협력 우수 학생연구자 선정 및 장학금 지원자 모집 |
| 3 | notice | general | 0.47797 | 산학협력단] 산학협력 우수 학생연구자 선정 및 장학금 지원자 모집 |
| 4 | notice | general | 0.47783 | 2025학년 2학기 『경기 RISE G7 (AI분야) 선도인재 장학금』 신청 안내 |
| 5 | notice | general | 0.47766 | 경기RISE사업] 2025학년도 2학기 『경기 RISE G7 (BIO 분야) 특성화 장학금』 신청 안내 |

## Q21  `장학금 신청 기간`
intent=장학 / form=정확키워드 / total=1308 / lat=177ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.50359 | 경기RISE사업] 2025학년도 2학기 『경기 RISE G7 (BIO 분야) 특성화 장학금』 신청 안내 |
| 2 | notice | general | 0.49032 | 2025학년 2학기 『경기 RISE G7 (AI분야) 학석사연계장려 장학생』 신청 안내 |
| 3 | notice | general | 0.49029 | 학기 『경기 RISE G7 (AI 분야) 특성화 장학금』 신청 안내 |
| 4 | notice | general | 0.4902 | 2025학년 2학기 『경기 RISE G7 (AI분야) 선도인재 장학금』 신청 안내 |
| 5 | notice | general | 0.49014 | X사업단] 2025학년도 2학기 특성화 교육훈련 혁신장학금 신청 안내 |

## Q22  `장학근 신청`
intent=장학 / form=오타 / total=874 / lat=169ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.47544 | 명지대학교 고시원] 2025-2학기 고시장학금 신청 안내 |
| 2 | notice | general | 0.47544 | 학기 고시장학금 신청 안내 |
| 3 | notice | general | 0.47518 | 2025학년도 2학기 에듀테크 리터러시 [학습법 특강(2차)] 비교과 프로그램 신청 안내 |
| 4 | notice | general | 0.47518 | 2025학년도 2학기 에듀테크 리터러시 [학습법 특강(1차)] 비교과 프로그램 신청 안내 |
| 5 | notice | general | 0.47475 | 2025학년도 2학기 에듀테크 리터러시 [학습법 특강(4차)] 비교과 프로그램 신청 안내 |

## Q23  `현장실습`
intent=취업/진로 / form=정확키워드 / total=171 / lat=98ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | career | 0.42821 | 현장실습] 2025년 2학기(9월~12월) 국내 현장실습 참여학생 모집 안내 |
| 2 | notice | career | 0.42817 | 현장실습] 2025-하계(7월~8월) 국내 현장실습 참여학생 모집 안내 |
| 3 | notice | career | 0.42754 | 현장실습] 2025학년도 1학기(3월~6월) 국내 현장실습학기제 참여학생 모집 안내 |
| 4 | notice | career | 0.4262 | 현장실습] 2025년 동계(1월~2월) 국내 현장실습 참여학생 모집 안내 |
| 5 | notice | career | 0.42436 | 현장실습] 2025년 하반기(9월~12월) ICT학점연계프로젝트인턴십(국내과정) 참여학생 모집 안내 |

## Q24  `채용설명회`
intent=취업/진로 / form=정확키워드 / total=272 / lat=288ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | career | 0.83819 | 링크알파코리아] LinqAlpha 채용 설명회 안내 |
| 2 | notice | career | 0.82819 | 에이어스] 2025년 공기업 채용 설명회 특강 무료 (기본: 10월 28일(화) 18시) 서울 오프라인 |
| 3 | notice | general | 0.46894 | 인문진로취업지원팀] 2026년 PTKOREA Campus Recruting(채용설명회 신청 안내 |
| 4 | notice | general | 0.46894 | 인문진로취업지원팀] 2026년 PTKOREA Campus Recruting(채용설명회 신청 안내 |
| 5 | notice | general | 0.46894 | 대학교육혁신원(대학교육혁신팀)] 대학혁신지원사업 기간제 전담직원 채용 공고 |

## Q25  `공모전`
intent=취업/진로 / form=정확키워드 / total=159 / lat=134ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.90616 | 정보보호팀] 2025년 안전한 개인정보보호 사례 공모전 안내(교육부 |
| 2 | notice | general | 0.90513999999999994 | 혁신사업] 2024학년도 명지대학교 박물관 이용후기 공모전 수상자 안내 |
| 3 | notice | general | 0.9044 | 인권센터] 폭력없는 캠퍼스 기획 공모전 안내 |
| 4 | notice | general | 0.9044 | 혁신사업] 2025학년도 인문 제6회/자연 제7회 우수리포트 공모전 안내 |
| 5 | notice | general | 0.90193 | 교수학습센터] 2026학년도 1학기 학습법 적용 우수 사례 공모전 [What-SSUP] 안내 |

## Q26  `인턴십`
intent=취업/진로 / form=정확키워드 / total=43 / lat=121ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | career | 0.84813 | 매력일자리 사업 '생성형 AI 기반 콘텐츠 마케터 양성 및 인턴십 연계과정' 교육생 |
| 2 | notice | career | 0.84803 | 마이온컴퍼니] 인턴십, 취업연계형 2025 G밸리 일경험 지원사업 프로그램 안내(~5/26까지 |
| 3 | notice | career | 0.84792 | 매력일자리 사업 '생성형 AI 기반 콘텐츠 마케터 양성 및 인턴십 연계과정' 모집 |
| 4 | notice | career | 0.84708 | 경기도일자리재단]2025년 「경기청년 해외진출」 사업 미국 인턴십 참여자 모집 |
| 5 | notice | career | 0.84514 | 레인보우커뮤니케이션]2025년 갓생 인턴십 모집 안내 / 2025년 농업·농촌 가치 확산 캠페인 |

## Q27  `취업 특강 있어?`
intent=취업/진로 / form=자연어질문 / total=469 / lat=129ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.87882 | 인문] 2025-1학기 온&오프 라이브 진로/취업(직무)특강(5월) 수강생 모집 안내 |
| 2 | notice | general | 0.8786 | 학기 동계방학 온&오프 라이브 진로/취업(직무)특강(2차) 수강생 모집 안내 |
| 3 | notice | general | 0.8786 | 학기 동계방학 온&오프 라이브 진로/취업(직무)특강(1차) 수강생 모집 |
| 4 | notice | general | 0.87846 | 학기 온&오프 라이브 진로/취업(직무)특강(3월) 수강생 모집 |
| 5 | notice | general | 0.8783 | X사업단] 2026학년도 AI·Bigdata·ICT 관련 취업 및 최신 기술 특강(2차) 참여 신청 안내 |

## Q28  `자격증 지원`
intent=취업/진로 / form=정확키워드 / total=719 / lat=138ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.88869 | 자연진로취업지원팀] MJU(My Job Upgrade) 재학생 취업경쟁력 강화를 위한 자격증 취득 지원 프로그램 안내 |
| 2 | notice | general | 0.88869 | 자연진로취업지원팀] MJU(My Job Upgrade) 재학생 취업경쟁력 강화를 위한 자격증 취득 지원 프로그램 안내 |
| 3 | notice | career | 0.82869 | 자연진로취업지원팀] MJU(My Job Upgrade) 재학생 취업경쟁력 강화를 위한 자격증 취득 지원 프로그램 안내 |
| 4 | notice | general | 0.46813 | 하계 비즈니스 스킬업 취업캠프」참여자 모집 (직무연수 이수증, 스피치 자격증 취득 |
| 5 | notice | general | 0.4652 | 명지대학교 학생지원팀] 도쿠시마현 관광협회와 함께 하는 도쿠시마 항공권 지원 프로그램 안내 |

## Q29  `학사경고`
intent=학칙/규정 / form=정확키워드 / total=118 / lat=124ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.44813 | 2026학년도 1학기가 초과학기인 학생 학사 안내문 (학위취득유예생 중 수강신청 희망자 |
| 2 | notice | academic | 0.44813 | 2025학년도 2학기가 초과학기인 학생 학사 안내문 (학위취득유예생 중 수강신청 희망자 |
| 3 | notice | academic | 0.44813 | 2025학년도 1학기가 초과학기인 학생 학사 안내(학위취득유예생 중 수강신청 희망자 포함 |
| 4 | broadcast |  | 0.2752 | 명지뉴스] 첫눈 내린 서울, 대설주의보 속 한파 경고 |
| 5 | notice | general | 0.23627 | [교수학습센터 |

## Q30  `출결 규정`
intent=학칙/규정 / form=정확키워드 / total=78 / lat=117ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | broadcast |  | 0.2752 | 명지뉴스] 분리배출 규정 강화? 가짜뉴스에 속지 마세요 |
| 2 | notice | general | 0.21838 | 2026 |
| 3 | notice | general | 0.21838 | 2025 |
| 4 | notice | general | 0.2178 | 국제교류학생클럽 |
| 5 | notice | general | 0.2178 | 국제교류학생클럽 |

## Q31  `등록금 환불`
intent=학칙/규정 / form=정확키워드 / total=150 / lat=1518ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.48614 | 2026학년도 1학기 대학 재학생 등록금 분할납부 안내 |
| 2 | notice | general | 0.48614 | 학기 대학 재학생 등록금 분할납부 안내 |
| 3 | notice | general | 0.48614 | 학기 대학 재학생 등록금 분할납부 안내 |
| 4 | notice | general | 0.48607 | 2026학년도 1학기 대학 재학생 등록금 구제 납부 안내 |
| 5 | notice | general | 0.48607 | 2026학년도 1학기 대학 재학생 등록금 구제 납부 안내 |

## Q32  `제적 기준`
intent=학칙/규정 / form=정확키워드 / total=297 / lat=231ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | scholarship | 0.82813 | 자연캠퍼스] 2026학년도 1학기 주거안정장학금 대학별 자체 기준 안내 |
| 2 | news | REPORT | 0.6852 | 이번 달부터 도서관 음료 반입 기준 완화 〈1105호 |
| 3 | notice | general | 0.23386 | [자연캠퍼스 |
| 4 | notice | general | 0.23175 | [자연캠퍼스 |
| 5 | notice | general | 0.23175 | [명지대학교 |

## Q33  `F학점 재수강 규정`
intent=학칙/규정 / form=영문혼용 / total=214 / lat=118ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.44911 | 2025학년도 후기(2026년 8월) 졸업예정자 학점 이수내역 확인 안내 (조기졸업 예정자 |
| 2 | notice | academic | 0.44911 | 2025학년도 후기(2026년 8월) 졸업예정자 학점 이수내역 확인 안내 (조기졸업 예정자 |
| 3 | notice | academic | 0.44911 | 2025학년도 전기(2026년 2월) 졸업예정자 학점 이수내역 확인 안내 |
| 4 | notice | academic | 0.4489 | 2026학년도 1학기 재수강 처리 신청원서 제출 안내 |
| 5 | notice | academic | 0.4489 | 2026학년도 1학기 재수강 처리 신청원서 제출 안내 |

## Q34  `장학금 환수`
intent=학칙/규정 / form=정확키워드 / total=241 / lat=120ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.46957 | 경기RISE사업] 2025학년도 2학기 『경기 RISE G7 (BIO 분야) 특성화 장학금』 신청 안내 |
| 2 | notice | general | 0.46957 | 학기 『경기 RISE G7 (AI 분야) 특성화 장학금』 신청 안내 |
| 3 | notice | general | 0.46931 | 2025학년 2학기 『경기 RISE G7 (AI분야) 선도인재 장학금』 신청 안내 |
| 4 | notice | general | 0.46931 | 2025학년도 2학기 명지 마일리지 장학금 수혜자 선정 안내 |
| 5 | notice | general | 0.46931 | 2025학년도 1학기 명지 마일리지 장학금 수혜자 선정 안내 |

## Q35  `도서관 좌석`
intent=시설/생활 / form=정확키워드 / total=75 / lat=99ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.46938 | 도서관] 2025학년도 전자정보박람회 개최 안내 |
| 2 | notice | general | 0.46931 | 인문캠퍼스 도서관] 2026학년도 「명지북크닉」 개최 안내 |
| 3 | notice | general | 0.46931 | 인문캠퍼스 도서관] 2026학년도 「명지북크닉」 개최 안내 |
| 4 | notice | general | 0.46926 | 도서관] 서버 이전에 따른 도서관 온라인 서비스 전체 중단 안내 |
| 5 | notice | general | 0.46926 | 도서관] 서버 이전에 따른 도서관 온라인 서비스 전체 중단 안내 |

## Q36  `기숙사 신청`
intent=시설/생활 / form=정확키워드 / total=861 / lat=141ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | scholarship | 0.4752 | 2026년 2학기 국가장학금 1차 신청 안내 |
| 2 | notice | general | 0.46938 | X사업단] 2026년도 AI·Data Science 워크숍(1차) 신청 안내 |
| 3 | notice | general | 0.46938 | 관련 취업 및 최신 기술 특강(2차) 참여 신청 안내 |
| 4 | notice | general | 0.46938 | X사업단] 2026학년도 AI·Bigdata·ICT 융합전공 학생설명회(2차) 참여 신청 안내 |
| 5 | notice | general | 0.46938 | Bigdata·ICT 관련 취업 및 최신기술 특강(1차) - 참여 신청 안내 |

## Q37  `셔틀버스 시간표`
intent=시설/생활 / form=정확키워드 / total=40 / lat=100ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.48588 | 신규 노선 추가] 2026학년도 1학기 자연캠퍼스 학기 중 통학·셔틀버스 운행 안내 |
| 2 | notice | general | 0.48588 | 신규 노선 추가] 2026학년도 1학기 자연캠퍼스 학기 중 통학·셔틀버스 운행 안내 |
| 3 | notice | general | 0.48544 | 2025학년도 2학기 자연캠퍼스 학기 중 통학·셔틀버스 운행 안내 |
| 4 | notice | general | 0.48468 | 2025학년도 1학기 자연캠퍼스 통학·셔틀버스 운행 안내 |
| 5 | notice | general | 0.48251 | 자연캠퍼스] 2025학년도 동계 방학기간 통학·셔틀버스 변경 운행 안내 |

## Q38  `학식 메뉴`
intent=시설/생활 / form=정확키워드 / total=17 / lat=147ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.2152 | [창업교육센터 |
| 2 | notice | general | 0.2152 | [창업교육센터 |
| 3 | notice | general | 0.2152 | [자연진로취업지원팀 |
| 4 | notice | general | 0.2152 | [자연진로취업지원팀 |
| 5 | notice | general | 0.2152 | [명지대학교 |

## Q39  `주차 등록`
intent=시설/생활 / form=정확키워드 / total=132 / lat=134ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.46922 | 2025학년도 동계 계절수업 안내(수강신청 및 등록 |
| 2 | notice | academic | 0.44926 | 2026학년도 하계 계절수업 안내(수강신청 및 등록) - 2026.5.22.시간표 업데이트 |
| 3 | notice | academic | 0.44926 | 2025학년도 하계 계절수업 안내(수강신청 및 등록)[2025.5.30 변동사항 반영 |
| 4 | notice | academic | 0.44922 | 2026학년도 하계 계절수업 안내(수강신청 및 등록 |
| 5 | notice | academic | 0.44922 | 2025학년도 동계 계절수업 안내(수강신청 및 등록 |

## Q40  `도서관 몇시까지 해?`
intent=시설/생활 / form=자연어질문 / total=71 / lat=142ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.86938 | 도서관] 2025학년도 전자정보박람회 개최 안내 |
| 2 | notice | general | 0.86931 | 인문캠퍼스 도서관] 2026학년도 「명지북크닉」 개최 안내 |
| 3 | notice | general | 0.86931 | 인문캠퍼스 도서관] 2026학년도 「명지북크닉」 개최 안내 |
| 4 | notice | general | 0.86926 | 도서관] 서버 이전에 따른 도서관 온라인 서비스 전체 중단 안내 |
| 5 | notice | general | 0.86926 | 도서관] 서버 이전에 따른 도서관 온라인 서비스 전체 중단 안내 |

## Q41  `열람실 예약`
intent=시설/생활 / form=동의어 / total=49 / lat=125ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.46731 | 인문/자연도서관] 정전으로 인한 전체 휴관(열람실 포함) 안내 / 2026. 1. 11.(일) 1일 |
| 2 | news | REPORT | 0.30589 | 제3공학관 열람실, 반도체공학과 강의실 변경에 학우들 “충분한 설명과 대안 마련 필요해” 〈1122호 |
| 3 | notice | general | 0.23377 | 도서관 |
| 4 | notice | general | 0.23299 | [도서관 |
| 5 | notice | general | 0.21894 | [총무인사팀 |

## Q42  `학생식당`
intent=시설/생활 / form=동의어 / total=1036 / lat=156ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.46941 | 사랑하는 학생 여러분 |
| 2 | notice | general | 0.46926 | 친애하는 학생 여러분 |
| 3 | notice | general | 0.46894 | 2026학년도 명지대학교 학생 홍보대사 ‘새빛모리’ 36기 모집 안내 |
| 4 | notice | general | 0.46894 | 2025학년도 명지대학교 학생 홍보대사 ‘새빛모리’ 35기 모집 안내 |
| 5 | notice | general | 0.46894 | 2025학년도 명지대학교 학생 홍보대사 ‘새빛모리’ 34기 모집 안내 |

## Q43  `동아리 모집`
intent=학생활동 / form=정확키워드 / total=1023 / lat=121ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | activity | 0.80333 | 서울 동아리 ON(대학생 동아리 사회 기여 활동 지원)」참여 동아리 추가 모집 |
| 2 | notice | activity | 0.80251 | 2025년 독서 동아리 활성화 사업」독서 동아리 모집 안내 |
| 3 | notice | activity | 0.8004 | 탄현청소년문화의집 2026년 대학생사회참여지원 동아리 모집 안내 |
| 4 | notice | activity | 0.8004 | 2025년 여름방학 대학생 교육기부 프로그램「쏙쏙캠프」대학생 동아리 모집 안내 |
| 5 | notice | general | 0.48559 | 국제교류학생클럽(어우라미) 27기 회원 모집 |

## Q44  `총학생회`
intent=학생활동 / form=정확키워드 / total=92 / lat=80ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | news | REPORT | 0.72765 | 자연캠 ‘ALT’ 총학생회, 한 해 동안 ALT(ALWAYS LISTEN&TRY) 했는가? 〈1110호(종강호 |
| 2 | news | REPORT | 0.72765 | 자연캠 ‘ALT’ 총학생회, 한 학기 동안 소통하고 행동한 결과는? 〈1103호(종강호 |
| 3 | news | REPORT | 0.7273 | 인문캠 ‘정진’ 총학생회, 한 학기 간 학우들을 위해 정진했는가? 〈1103호(종강호 |
| 4 | news | REPORT | 0.72708 | 인문캠 ‘정진’ 총학생회, 한 해 동안 올곧게 정진했는가? 〈1110호(종강호 |
| 5 | news | REPORT | 0.72683 | 탄핵안 공개된 자연캠 'PIER' 총학생회… 갈등은 합의로 마무리 〈1152호 |

## Q45  `학생회비`
intent=학생활동 / form=정확키워드 / total=1029 / lat=153ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.46941 | 사랑하는 학생 여러분 |
| 2 | notice | general | 0.46926 | 친애하는 학생 여러분 |
| 3 | notice | general | 0.46894 | 2026학년도 명지대학교 학생 홍보대사 ‘새빛모리’ 36기 모집 안내 |
| 4 | notice | general | 0.46894 | 2025학년도 명지대학교 학생 홍보대사 ‘새빛모리’ 35기 모집 안내 |
| 5 | notice | general | 0.46894 | 2025학년도 명지대학교 학생 홍보대사 ‘새빛모리’ 34기 모집 안내 |

## Q46  `축제 일정`
intent=학생활동 / form=정확키워드 / total=500 / lat=96ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.46894 | 2025학년도 2학기 수강신청 일정 안내 |
| 2 | notice | general | 0.46857 | 스포츠·예술대학] 2026학년도 대학 U리그(농구, 배구, 축구) 일정 안내 |
| 3 | notice | general | 0.46857 | 스포츠·예술대학] 2026학년도 대학 U리그(농구, 배구, 축구) 일정 안내 |
| 4 | notice | general | 0.4678 | 자연캠퍼스] 학생예비군훈련 일정 연기 안내 |
| 5 | notice | general | 0.46731 | 인문캠퍼스] 전기안전 법정검사 진행으로 인한 정전 일정 안내 |

## Q47  `대동제`
intent=학생활동 / form=동의어 / total=11 / lat=80ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.90193 | 2025학년도 명지대학교 인문캠퍼스 대동제 「온 ; 溫」 행사일정 안내 |
| 2 | news | REPORT | 0.72708 | 위엄과 낭만이 가득했던 이틀, 양캠 대동제 개최 〈1134호 |
| 3 | news | REPORT | 0.72683 | 우리 대학서 제37회 전국 아랍어과 대동제 진행돼 〈1142호 |
| 4 | news | REPORT | 0.72683 | 인문캠  대동제  ‘천진낭만’,  학과 부스  ‘입점비’  문제  제기돼 〈1134호 |
| 5 | news | REPORT | 0.72616 | MAJESTY와 천진낭만, 풍성했던 올해의 대동제 〈1134호 |

## Q48  `봉사활동`
intent=학생활동 / form=정확키워드 / total=272 / lat=108ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.5049 | 사회봉사단] ‘그린나래’와 함께하는 노인복지관 급식 봉사 안내(5차, 6차 |
| 2 | notice | general | 0.50484 | 사회봉사단]‘그린나래’와 함께하는 노인복지관 급식 봉사 안내 |
| 3 | notice | general | 0.50438 | 사회봉사단] ‘그린나래’와 함께하는 노인복지관 급식 봉사 안내(4차 |
| 4 | notice | general | 0.48767 | 사회봉사단] ‘그린나래’와 함께하는 노인복지관 급식 봉사 안내 |
| 5 | notice | general | 0.48685 | 사회봉사단] ‘그린나래’와 함께하는 클린캠퍼스 봉사 안내(2차 |

## Q49  `증명서 발급`
intent=행정 / form=정확키워드 / total=218 / lat=296ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.46731 | 2025년 대학원 교육비 납입증명서 조회 및 발급 안내 |
| 2 | notice | general | 0.46731 | 2025년 대학 교육비 납입증명서 조회 및 발급 안내 |
| 3 | notice | general | 0.46731 | 2024년 대학 교육비 납입증명서 조회 및 발급 안내 |
| 4 | community | NOTICE | 0.32043 | EP.1] 학생증명서 발급 |
| 5 | notice | general | 0.23544 | 2026학년도 |

## Q50  `학생증 재발급`
intent=행정 / form=정확키워드 / total=190 / lat=165ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.47226 | 학생증(구 디자인) 재발급 서비스 종료 안내 |
| 2 | notice | general | 0.46298 | 2025년 대학 교육비 납입증명서 조회 및 발급 안내 |
| 3 | notice | general | 0.46298 | 2025년 대학원 교육비 납입증명서 조회 및 발급 안내 |
| 4 | notice | general | 0.46298 | 2024년 대학 교육비 납입증명서 조회 및 발급 안내 |
| 5 | community | NOTICE | 0.30143 | EP.1] 학생증명서 발급 |

## Q51  `전자출결`
intent=행정 / form=정확키워드 / total=49 / lat=136ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.46423 | 전자출결 시스템 사용법 안내 |
| 2 | notice | general | 0.21872 | 2025학년도 |
| 3 | notice | general | 0.21813 | [산학협력단 |
| 4 | notice | general | 0.21813 | 2025 |
| 5 | notice | general | 0.21813 | [ 공학교육혁신센터 |

## Q52  `재학증명서 어떻게 떼?`
intent=행정 / form=자연어질문 / total=234 / lat=118ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | news | REPORT | 0.28039 | 일은 어떻게 만들어지는가』 저자 이종훈 교수, 북토크 개최 〈1153호 |
| 2 | news | REPORT | 0.27912 | 새학기, 새로워진 ‘천원의 아침밥’ 어떻게 달라졌을까? 〈1126호 |
| 3 | news | SOCIETY | 0.27912 | 혐오로 길을 잃다, 인셀은 어떻게 테러리스트가 되었나 〈1124호(개강호 |
| 4 | news | REPORT | 0.27912 | 우리 대학 MCC관 내 무단 용도변경 확인, 향후 대처 어떻게 하나? 〈1109호 |
| 5 | news | REPORT | 0.27912 | 새롭게 돌아온 인문캠 학생식당, 무엇이 어떻게 달라졌을까? 〈1108호(창간기념호 |

## Q53  `LMS 사용법`
intent=행정 / form=영문혼용 / total=43 / lat=114ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.49884 | 전자출결 시스템 사용법 안내 |
| 2 | notice | general | 0.46934 | 원격교육센터] LMS 서비스 일시 중단 안내 (AI 기능 적용 및 서버 재부팅) / 2026.04.06.(월)17시 |
| 3 | notice | general | 0.46934 | 원격교육센터] LMS 서비스 일시 중단 안내 (AI 기능 적용 및 서버 재부팅) / 2026.04.06.(월)17시 |
| 4 | notice | general | 0.46922 | 원격교육센터] 신규 LMS 안정화 관련 안내 |
| 5 | notice | general | 0.46922 | 원격교육센터] 신규 LMS 안정화 관련 안내 |

## Q54  `MYIWEB`
intent=행정 / form=영문혼용 / total=31 / lat=85ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.28571 | 전자출결 |
| 2 | notice | general | 0.2544 | 2025학년도 |
| 3 | notice | general | 0.24965 | [사회봉사단 |
| 4 | notice | general | 0.24965 | [사회봉사단 |
| 5 | notice | general | 0.24965 | [전산정보원 |

## Q55  `명대신문`
intent=뉴스/방송 / form=정확키워드 / total=59 / lat=87ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | news | SOCIETY | 0.69786 | 신문 발행의 마지막 관문, 인쇄현장에서 유흥상 파트장을 만나다 〈1132호 |
| 2 | news | REPORT | 0.32268 | 여러분과 함께한 71년, 명대신문 전시회 개최 〈1138호 |
| 3 | news | REPORT | 0.32079 | 명대신문, 제14회 시사IN 대학기자상 시상식에서  자리를 빛내다 〈1113호 |
| 4 | news | SOCIETY | 0.32 | 명지의 역사를 함께 써온 이름, 명대신문 56기 윤휘종 〈1151호 |
| 5 | news | REPORT | 0.2928 | 명대신문 단신 〈1112호 |

## Q56  `MJU 뉴스`
intent=뉴스/방송 / form=영문혼용 / total=87 / lat=92ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.46938 | 자연진로취업지원팀] MJU(My Job Upgrade) 재학생 취업경쟁력 강화를 위한 자격증 취득 지원 프로그램 안내 |
| 2 | notice | general | 0.46938 | 자연진로취업지원팀] MJU(My Job Upgrade) 재학생 취업경쟁력 강화를 위한 자격증 취득 지원 프로그램 안내 |
| 3 | notice | general | 0.46731 | 2025년 MJU 대학혁신지원사업 우수 성과 공유 안내 |
| 4 | notice | general | 0.46731 | 창업교육센터] 2025 MJU 글로벌 창업 인재 양성 프로그램(일본도쿄) 신청 안내 |
| 5 | notice | career | 0.40938 | 자연진로취업지원팀] MJU(My Job Upgrade) 재학생 취업경쟁력 강화를 위한 자격증 취득 지원 프로그램 안내 |

## Q57  `명지대 방송국`
intent=뉴스/방송 / form=정확키워드 / total=167 / lat=122ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | broadcast |  | 0.71487 | 명지대 방송국에서 신입국원을 모집합니다! #shorts #명지대 #명지대학교 #MBS #신입 #신입국원 #새내기 #아나운서 #PD #기자 #보도... |
| 2 | broadcast |  | 0.71171 | 명지대 방송국?에서 영상제? 한다는데?  #명지대 #영상제 #shorts |
| 3 | broadcast |  | 0.70991 | 제20회 명지대학교 방송국 영상제 MUFF 본선 진출작 피는 꽃 미리보기 #명지대 #MUFF #방송국 #영상제 #영화 #다큐 #애착 #short... |
| 4 | broadcast |  | 0.70982 | 제20회 명지대학교 방송국 영상제 MUFF 본선 진출작 BUT 미리보기 #명지대  #MUFF #방송국 #영상제 #영화 #다큐 #애착 #short... |
| 5 | broadcast |  | 0.70982 | 제20회 명지대학교 방송국 영상제 MUFF 홍보 영상 #명지대 #MUFF #방송국 #영상제 #영화 #다큐 #애착 #shorts |

## Q58  `요즘 학교 이슈`
intent=뉴스/방송 / form=모호 / total=379 / lat=136ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.4636 | 인사혁신처] 2026년 지역인재 7급 수습직원 선발 시험 학교 추천자 모집 |
| 2 | notice | general | 0.46298 | 인문보건의료센터] "갑자기 아프면 어디로 가야 할까?" 학교 근처 응급의료기관 안내 |
| 3 | notice | general | 0.46298 | 인문보건의료센터] "갑자기 아프면 어디로 가야 할까?" 학교 근처 응급의료기관 안내 |
| 4 | notice | career | 0.40441 | 인사혁신처] 2026년 지역인재 7급 수습직원 선발 시험 학교 추천자 모집 |
| 5 | notice | career | 0.40441 | 2025년 지역인재 7급 수습직원 선발시험 학교 추천자 모집 |

## Q59  ``
intent=엣지 / form=노이즈 / total=3975 / lat=128ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.23 | 전자출결 시스템 사용법 안내 |
| 2 | notice | scholarship | 0.21 | 2026년 2학기 국가장학금 1차 신청 안내 |
| 3 | notice | general | 0.2 | [박물관] 명지문화유산답사 제48회 참가자 모집 안내 |
| 4 | notice | general | 0.2 | [자연보건의료] 감염병예방! 결핵검진 안내 |
| 5 | notice | general | 0.2 | [대학교육혁신원] 2026학년도 1학기 전공능력진단검사 실시 안내 |

## Q60  `ㅁㅈㄷ`
intent=엣지 / form=노이즈 / total=0 / lat=61ms / zero=True

## Q61  `명지대학교`
intent=엣지 / form=정확키워드 / total=757 / lat=159ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.90857 | 원격교육센터] 명지대학교 "카피킬러 캠퍼스" 도입 안내 |
| 2 | notice | general | 0.90857 | 원격교육센터] 명지대학교 "카피킬러 캠퍼스" 도입 안내 |
| 3 | notice | general | 0.90822 | 자연캠퍼스] 명지대학교 ˝클린캠퍼스˝ 참가자 모집 안내 |
| 4 | notice | general | 0.90779 | 혁신사업 - 에너지 소재 사업단] 명지대학교 에너지 소재 사업단 R&D 아이디어 경진대회 공고 |
| 5 | notice | general | 0.90779 | 혁신사업 - 에너지 소재 사업단] 명지대학교 에너지 소재 사업단 R&D 아이디어 경진대회 공고 |

## Q62  `ㅎㅇ`
intent=엣지 / form=노이즈 / total=0 / lat=66ms / zero=True

## Q63  `코로나`
intent=엣지 / form=정확키워드 / total=18 / lat=97ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.25339 | [명지대학교 |
| 2 | notice | general | 0.25339 | [명지대학교 |
| 3 | notice | general | 0.25339 | [명지대학교 |
| 4 | notice | general | 0.25339 | [명지대학교 |
| 5 | notice | general | 0.25339 | 2026년 |

## Q64  `비교과 프로그램`
intent=학사/수강 / form=정확키워드 / total=654 / lat=151ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.50336 | 교수학습센터] 2025학년도 2학기 에듀테크 리터러시 [학습법 특강(4차)] 비교과 프로그램 신청 안내 |
| 2 | notice | general | 0.50325 | 교수학습센터] 2025학년도 2학기 에듀테크 리터러시 [학습법 특강(2차)] 비교과 프로그램 신청 안내 |
| 3 | notice | general | 0.50325 | 교수학습센터] 2025학년도 2학기 에듀테크 리터러시 [학습법 특강(1차)] 비교과 프로그램 신청 안내 |
| 4 | notice | general | 0.50325 | 교수학습센터] 2025학년도 1학기 에듀테크 리터러시 ˝학습법 특강(3차)˝ 비교과 프로그램 신청 안내 |
| 5 | notice | general | 0.50325 | 교수학습센터] 2025학년도 1학기 에듀테크 리터러시 [학습법 특강(2차)] 비교과 프로그램 신청 안내 |

## Q65  `마일리지`
intent=학사/수강 / form=정확키워드 / total=98 / lat=109ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.90939 | 2025학년도 2학기 명지 마일리지 장학금 수혜자 선정 안내 |
| 2 | notice | general | 0.90934 | 2026학년도 1학기 명지 마일리지 장학금 수혜자 선정 안내 |
| 3 | notice | general | 0.90934 | 2025학년도 1학기 명지 마일리지 장학금 수혜자 선정 안내 |
| 4 | notice | career | 0.84193 | 산학협력 마일리지 활용(재무빅데이터분석사, 국가공인 PC정비사 자격) 지원 안내 |
| 5 | notice | activity | 0.81559 | 읽는 대한민국 <책수다 마일리지> 안내 |

## Q66  `학점교류`
intent=학사/수강 / form=정확키워드 / total=195 / lat=120ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.48525 | 하계 계절학기 학점교류 신청 안내 |
| 2 | notice | academic | 0.48521 | 하계 계절학기 학점교류 신청 안내 |
| 3 | notice | academic | 0.48521 | 하계 계절학기 학점교류 신청 안내 |
| 4 | notice | academic | 0.48521 | 동계 계절학기 학점교류 신청 안내 |
| 5 | notice | academic | 0.46728 | 2026학년도 1학기 학점교류 신청안내 |

## Q67  `교환학생`
intent=학사/수강 / form=정확키워드 / total=20 / lat=87ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.89559 | 2026학년도 국제학생교류클럽(어우라미) 외국인 교환학생 주관 축제 행사(Myongji Sky Pass) 안내 |
| 2 | notice | general | 0.89559 | 2026학년도 국제학생교류클럽(어우라미) 외국인 교환학생 주관 축제 행사(Myongji Sky Pass) 안내 |
| 3 | notice | general | 0.25653 | [명지대학교 |
| 4 | notice | general | 0.25653 | [국제교류처 |
| 5 | notice | general | 0.25339 | 국제교류학생클럽 |

## Q68  `군휴학`
intent=학사/수강 / form=정확키워드 / total=116 / lat=84ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.23492 | 2026학년도 |
| 2 | notice | academic | 0.23492 | 2025학년도 |
| 3 | notice | academic | 0.23479 | 2025학년도 |
| 4 | notice | academic | 0.23461 | 2026학년도 |
| 5 | notice | academic | 0.23461 | 2025학년도 |

## Q69  `일반휴학 군휴학 차이`
intent=학사/수강 / form=복합 / total=161 / lat=145ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.21855 | [직장예비군연대 |
| 2 | notice | general | 0.21855 | [직장예비군연대 |
| 3 | notice | general | 0.21843 | [자연캠퍼스 |
| 4 | notice | general | 0.21843 | [자연캠퍼스 |
| 5 | notice | general | 0.2165 | 2026학년도 |

## Q70  `졸업유예`
intent=학사/수강 / form=정확키워드 / total=276 / lat=217ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | academic | 0.48493 | 2024학년도 전기(2025년 2월) 졸업대상자 학사학위 취득 유예(졸업유예) 신청 안내 |
| 2 | notice | academic | 0.48471 | 2025학년도 전기(2026년 2월) 졸업대상자학사학위 취득 유예(졸업유예) 신청 안내 |
| 3 | notice | academic | 0.48471 | 2024학년도 후기(2025년 8월) 졸업대상자 학사학위 취득 유예(졸업유예) 신청 안내 |
| 4 | notice | career | 0.40946 | 인문] 졸업생 특화프로그램 - 졸업(예정)자의 자소서&면접 완성을 위한 '경험정리' 참여자 모집 |
| 5 | notice | career | 0.40941 | MJ대학일자리플러스센터] 졸업(예정)자를 위한 [MJ집중케어센터 |

## Q71  `조기취업`
intent=취업/진로 / form=정확키워드 / total=354 / lat=141ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.46922 | 인문] 2025-1학기 온&오프 라이브 진로/취업(직무)특강(5월) 수강생 모집 안내 |
| 2 | notice | general | 0.4691 | 학기 온&오프 라이브 진로/취업(직무)특강(3월) 수강생 모집 |
| 3 | notice | general | 0.4691 | 학기 동계방학 온&오프 라이브 진로/취업(직무)특강(2차) 수강생 모집 안내 |
| 4 | notice | general | 0.4691 | 학기 동계방학 온&오프 라이브 진로/취업(직무)특강(1차) 수강생 모집 |
| 5 | notice | general | 0.46857 | X사업단] 2026학년도 AI·Bigdata·ICT 관련 취업 및 최신 기술 특강(2차) 참여 신청 안내 |

## Q72  `교환학생 파견`
intent=학사/수강 / form=정확키워드 / total=37 / lat=83ms / zero=False

| # | type | category | score | title |
|---|------|----------|-------|-------|
| 1 | notice | general | 0.4652 | 2026학년도 국제학생교류클럽(어우라미) 외국인 교환학생 주관 축제 행사(Myongji Sky Pass) 안내 |
| 2 | notice | general | 0.4652 | 2026학년도 국제학생교류클럽(어우라미) 외국인 교환학생 주관 축제 행사(Myongji Sky Pass) 안내 |
| 3 | notice | career | 0.40894 | 세종학당재단] 2026년 세종학당 문화인턴 파견 사업 설명회 개최 안내 |
| 4 | notice | activity | 0.3852 | 2026년 세종학당 문화인턴 파견 사업 설명회 개최 안내 |
| 5 | notice | activity | 0.3852 | 2026년 세종학당 문화인턴 파견 사업 설명회 안내 |


