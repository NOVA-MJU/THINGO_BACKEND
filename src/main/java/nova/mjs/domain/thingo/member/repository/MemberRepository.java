package nova.mjs.domain.thingo.member.repository;


import nova.mjs.domain.thingo.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByUuid(UUID uuid);
    boolean existsByEmail(String email);
    Optional<Member> findByEmail(String email);
    // 구글 이메일은 소문자로 받지만 이메일 가입 회원은 입력한 대소문자 그대로 저장돼 있을 수 있다
    Optional<Member> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);
    boolean existsByNickname(String nickname);
    boolean existsByStudentNumber(String studentNumber);


}
