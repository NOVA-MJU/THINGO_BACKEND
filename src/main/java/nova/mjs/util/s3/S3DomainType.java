package nova.mjs.util.s3;

import lombok.Getter;

/**
 * S3에 업로드되는 파일들의 도메인 구분을 위한 Enum이 구성된 파일입니다.
 * 각 항목은 고유한 prefix 경로를 가짐.
 */
@Getter
public enum S3DomainType {

    DEFAULT_THUMBNAIL_URL("https://thingo.kr/static/images/member/profiles/a5fe8971-c436-4794-96d7-8dd29583d16a/0ac4e3ad18749a7029ea8876644c1a2b5bafc07aa31dc5d56a9d7dde8df07a6a.jpeg"),
    COMMUNITY_TEMP("static/images/boards/temp/"), // 삭제 에정
    COMMUNITY_POST("static/images/boards/"),
    PROFILE_IMAGE("static/images/member/profiles/"),
    DEPARTMENT_LOGO("static/images/departments/logos/"),
    DEPARTMENT_SCHEDULE("static/images/departments/schedules/"),
    STUDENT_COUNCIL_NOTICE("static/image/departments/student-council/notice"),
    BANNER("static/images/banners/");

    private final String prefix;

    S3DomainType(String prefix) {
        this.prefix = prefix;
    }
}

