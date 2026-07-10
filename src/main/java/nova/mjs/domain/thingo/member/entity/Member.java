package nova.mjs.domain.thingo.member.entity;

import jakarta.persistence.*;
import lombok.*;
import nova.mjs.admin.account.DTO.AdminDTO;
import nova.mjs.domain.thingo.department.entity.enumList.College;
import nova.mjs.domain.thingo.member.DTO.MemberDTO;
import nova.mjs.domain.thingo.department.entity.enumList.DepartmentName;
import nova.mjs.util.entity.BaseEntity;

import java.util.UUID;

// Entity
@Entity
@Table(name = "member")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID uuid;

    @Enumerated(EnumType.STRING) // `role` 필드 추가
    @Column(nullable = false)
    private Role role;  // Role enum 타입으로 설정

    @Column(nullable = false)
    private String name;

    @Column
    private String profileImageUrl; // 프로필 / StudentCouncil: 학생회 로고

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private College college;

    @Enumerated(EnumType.STRING)
    @Column
    private DepartmentName departmentName;


    private String studentNumber;

    public enum Gender {
        MALE, FEMALE, OTHERS;

        public static Gender fromString(String value) {
            try {
                return Gender.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException("Invalid gender value: " + value);
            }
        }
    }

    public enum Role {
        USER, ADMIN, OPERATOR, MENTOR
    }

    public static Member create(MemberDTO.MemberRegistrationRequestDTO memberDTO, String encodePassword) {
        return Member.builder()
                .uuid(UUID.randomUUID()) // UUID 자동 생성
                .name(memberDTO.getName())
                .email(memberDTO.getEmail())
                .password(encodePassword)
                .nickname(memberDTO.getNickname())
                .profileImageUrl(memberDTO.getProfileImageUrl())
                .college(memberDTO.getCollege())
                .departmentName(memberDTO.getDepartmentName())
                .studentNumber(memberDTO.getStudentNumber())
                .role(Role.USER)
                .build();
    }

    public void update(MemberDTO.MemberUpdateRequestDTO memberDTO) {
        this.name = getOrDefault(memberDTO.getName(), this.name);
        this.nickname = getOrDefault(memberDTO.getNickname(), this.nickname);
        this.departmentName = getOrDefault(memberDTO.getDepartmentName(), this.departmentName);
        this.college = getOrDefault(memberDTO.getCollege(), this.college);
        this.studentNumber = getOrDefault(memberDTO.getStudentNumber(), this.studentNumber);
        this.profileImageUrl = getOrDefault(memberDTO.getProfileImageUrl(), this.profileImageUrl);
        this.gender = memberDTO.getGender() != null ? Gender.fromString(memberDTO.getGender()) : this.gender;
    }


    // ============ 어드민 계정 =============== //


    // 초기 어드민 계정을 생성할 경우, UUID, 이메일, 임시 비밀버호, 역할, 성별은 제공한다.
    public static Member createAdminInit(AdminDTO.StudentCouncilInitRegistrationRequestDTO memberDTO, String encodePassword) {
        return Member.builder()
                .uuid(UUID.randomUUID()) // UUID 자동 생성\
                .email(memberDTO.getEmail())
                .password(encodePassword)
                .name(memberDTO.getName())
                .college(memberDTO.getCollege())
                .departmentName(memberDTO.getDepartmentName())
                .role(Role.ADMIN)
                .gender(Gender.OTHERS)
                .studentNumber("NONE")
                .build();
    }

    // 계정을 받은 경우 회원가입, 혹은 업데이트 두가지 경우 모두 사용가능하다
    // 1. 계정이름(= 학생회 명, 닉네임은 계정 이름과 동일하다. 2. 변경할 비밀번호, 3. 소속 학과, 4. 학생회 프로필 로고
    // 5. 슬로건 , 인스타그램, 공식홈페이지, Description  => Department 에서 받음
    public void updateAdmin(AdminDTO.StudentCouncilUpdateDTO memberDTO) {
        this.name = getOrDefault(memberDTO.getName(), this.name); // 학생회 명
        this.nickname = name;
        this.college = getOrDefault(memberDTO.getCollege(), this.college);
        this.departmentName = getOrDefault(memberDTO.getDepartmentName(), this.departmentName); // 소속학과
        this.profileImageUrl = getOrDefault(memberDTO.getProfileImageUrl(), this.profileImageUrl); // 학생회 프로필 로고
    }


    public void updatePassword(String encodedNewPassword) {
        this.password = getOrDefault((encodedNewPassword), this.password);
    }

    private <T> T getOrDefault(T newValue, T currentValue) {
        return newValue != null ? newValue : currentValue;
    }
}
