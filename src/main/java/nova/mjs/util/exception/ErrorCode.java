package nova.mjs.util.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 서버 에러 (S)
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "[MJS] 내부 서버 에러가 발생했습니다."),
    API_UNKNOWN_FINISH_REASON(HttpStatus.INTERNAL_SERVER_ERROR, "API_UNKNOWN_FINISH_REASON", "[MJS] 알 수 없는 이유로 응답을 불러올 수 없습니다."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR", "[MJS] 데이터베이스 오류가 발생했습니다."),

    //스케쥴러 관련 에러
    SCHEDULER_TASK_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SCHEDULER_TASK_FAILED", "[MJS] 스케줄러 작업이 실패했습니다."),
    SCHEDULER_CRON_INVALID(HttpStatus.BAD_REQUEST, "SCHEDULER_CRON_INVALID", "[MJS] 잘못된 Cron 표현식입니다."),
    SCHEDULER_UNKNOWN_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SCHEDULER_UNKNOWN_ERROR", "[MJS] 알 수 없는 스케줄러 오류가 발생했습니다."),

    // JWT 관련 에러
    INVALID_TOKEN_FORMAT(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN_FORMAT", "[MJS] JWT 형식이 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "[MJS] JWT 토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "[MJS] 유효하지 않거나 일반적이지 않은 JWT 토큰입니다."),
    TOKEN_MALFORMED(HttpStatus.UNAUTHORIZED, "TOKEN_MALFORMED", "[MJS] JWT 토큰이 변조되었거나 형식이 올바르지 않습니다."),
    TOKEN_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "TOKEN_SIGNATURE_INVALID", "[MJS] JWT 토큰의 서명이 유효하지 않습니다."),
    TOKEN_UNSUPPORTED(HttpStatus.UNAUTHORIZED, "TOKEN_UNSUPPORTED", "[MJS] 지원되지 않는 JWT 토큰입니다."),
    TOKEN_NOT_PROVIDED(HttpStatus.UNAUTHORIZED, "TOKEN_NOT_PROVIDED", "[MJS] JWT 토큰이 제공되지 않았습니다."),
    NOT_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "NOT_REFRESH_TOKEN", "[MJS] 제공된 토큰은 Refresh Token이 아닙니다."),
    REFRESH_TOKEN_PARSE_FAILED(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_PARSE_FAILED", "[MJS] Refresh Token에서 사용자 정보를 추출할 수 없습니다."),

    // 마이페이지 에러
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "[MJS] 해당 사용자를 찾을 수 없습니다."),

    // 요청 에러 (R)
    INVALID_PARAM_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_PARAM_REQUEST", "[MJS] 요청된 파람 값이 잘못되었습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "[MJS] 요청된 값이 잘못되었습니다."),
    OFFSET_IS_LESS_THEN_ONE(HttpStatus.BAD_REQUEST, "OFFSET_IS_LESS_THEN_ONE", "[MJS] offset은 1부터 시작합니다."),
    LIMIT_IS_LESS_THEN_ONE(HttpStatus.BAD_REQUEST, "LIMIT_IS_LESS_THEN_ONE", "[MJS] limit은 1부터 시작합니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "[MJS] 잘못된 입력입니다."),
    EMAIL_IS_INVALID(HttpStatus.BAD_REQUEST, "EMAIL_IS_INVALID", "[MJS] 잘못된 이메일 형식입니다."),
    NICKNAME_IS_INVALID(HttpStatus.BAD_REQUEST, "EMAIL_IS_INVALID", "[MJS] 회원 닉네임이 잘못된 형식입니다."),
    INVALID_S3_URL(HttpStatus.BAD_REQUEST, "INVALID_S3_URL", "[MJS] 유효하지 않은 S3 Url입니다."),

    // 공지사항 에러 관련 추가 코드
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND", "[MJS] 해당 조건의 공지사항을 찾을 수 없습니다."),
    STUDENT_COUNCIL_NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "STUDENT_COUNCIL_NOTICE_NOT_FOUND", "[MJS] 해당 조건의 학과 공지사항을 찾을 수 없습니다."),

    // 자유 게시판 관련 에러(C)
    COMMUNITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND", "[MJS] 요청한 게시판을 찾을 수 없습니다."),

    //식단 관련 에러
    WEEKLYMENU_NOT_FOUND(HttpStatus.NOT_FOUND, "WEEKLYMENU_NOT_FOUND", "[MJS] 식단 내용을 찾을 수 없습니다."),

    //명대신문 관련 에러
    NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "NEWS_NOT_FOUND", "[MJS] 요청한 기사 내용을 찾을 수 없습니다."),

    // S3 이미지 증록 에러
    S3_IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S3_IMAGE_UPLOAD_FAILED", "[MJS] S3 이미지 업로드에서 에러가 발생하였습니다."),
    
    
    // 회원 에러(M)
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND,"MEMBER_NOT_FOUND" ,"[MJS] 회원 정보를 찾을 수 없습니다." ),
    PASSWORD_IS_INVALID(HttpStatus.BAD_REQUEST, "PASSWORD_IS_INVALID", "[MJS] 비밀번호가 잘못되었습니다" ),
    SAME_PASSWORD_NOT_ALLOWED(HttpStatus.BAD_REQUEST,"SAME_PASSWORD_NOT_ALLOWED" , "[MJS] 이전과 동일한 비밀번호로는 변경할 수 없습니다."),
    DUPLICATE_EMAIL_EXCEPTION(HttpStatus.BAD_REQUEST,"DUPLICATE_EMAIL_EXCEPTION", "[MJS] 이미 존재하는 이메일입니다." ),
    DUPLICATE_STUDENT_NUMBER_EXCEPTION(HttpStatus.BAD_REQUEST,"DUPLICATE_STUDENT_NUMBER_EXCEPTION", "[MJS] 이미 존재하는 학번입니다." ),
    INVALID_STUDENT_NUMBER_EXCEPTION(HttpStatus.BAD_REQUEST,"INVALID_STUDENT_NUMBER_EXCEPTION", "[MJS] 요청하신 학번이 8자리 숫자로 이루어져있지 않습니다." ),
    DUPLICATE_NICKNAME_EXCEPTION(HttpStatus.BAD_REQUEST,"DUPLICATE_NICKNAME_EXCEPTION", "[MJS] 이미 존재하는 닉네임입니다." ),

    // 날씨 에러
    API_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "API_CALL_FAILED", "[MJS] API 호출 중 오류 발생하였습니다."),
    JSON_PARSING_FAILED(HttpStatus.BAD_REQUEST, "JSON_PARSING_FAILED", "[MJS] JSON 데이터 파싱 오류입니다."),
    NO_DATA_FOUND(HttpStatus.NOT_FOUND, "NO_DATA_FOUND", "[MJS] 저장된 날씨 데이터가 없습니다."),

    // 어드민 에러
    ADMIN_ID_MISMATCH(HttpStatus.BAD_REQUEST, "ADMIN_ID_MISMATCH", "[MJS] 사전 등록된 관리자 ID와 일치하지 않습니다."),
    ADMIN_ID_MISMATCH_WITH_DEPARTMENT_EXCEPTION(HttpStatus.BAD_REQUEST, "ADMIN_ID_MISMATCH_WITH_DEPARTMENT_EXCEPTION", "[MJS] 등록된 관리자와 담당 학과가 일치하지 않습니다."),

    //학과별 서비스 관련 에러
    COLLEGE_NOT_FOUND(HttpStatus.NOT_FOUND, "COLLEGE_NOT_FOUND", "[MJS] 존재하지 않는 단과대입니다."),
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND", "[MJS] 존재하지 않는 학과입니다."),
    DEPARTMENT_ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "DEPARTMENT_ADMIN_NOT_FOUND", "[MJS] 해당 관리자 계정을 찾을 수 없습니다."),
    DEPARTMENT_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "DEPARTMENT_SCHEDULE_NOT_FOUND", "[MJS] 존재하지 않는 학과 일정입니다."),

    // 멘토관 서비스 관련 에러
    MENTOR_NOT_FOUND(HttpStatus.NOT_FOUND,"MENTOR_NOT_FOUND" ,"[MJS] 멘토 정보를 찾을 수 없습니다." ),

    // 명대방송국 관련 에러
    BROADCAST_SYNC_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "BROADCAST_SYNC_FAILED", "[MJS] Broadcast 동기화에 실패했습니다."),

    // 버스 도착 정보 관련 에러
    BUS_ARRIVAL_API_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "BUS_ARRIVAL_API_CALL_FAILED", "[MJS] 버스 도착 정보 API 호출 중 오류가 발생했습니다."),
    BUS_ARRIVAL_PARSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "BUS_ARRIVAL_PARSE_FAILED", "[MJS] 버스 도착 정보 파싱 중 오류가 발생했습니다."),
    BUS_ARRIVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "BUS_ARRIVAL_NOT_FOUND", "[MJS] 해당 정류장의 버스 도착 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String error;
    private final String message;

    ErrorCode(final HttpStatus status, final String error, final String message) {
        this.status = status;
        this.error = error;
        this.message = message;
    }
}
