package nova.mjs.domain.thingo.keywordAlarm.repository;

import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmCategory;
import nova.mjs.domain.thingo.keywordAlarm.entity.KeywordSubscription;
import nova.mjs.domain.thingo.member.entity.Member;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KeywordSubscriptionRepository
        extends JpaRepository<KeywordSubscription, Long>, KeywordSubscriptionQueryRepository {

    /** 중복 등록 방지 - 회원이 같은 키워드를 이미 등록했는지 */
    boolean existsByMemberAndKeyword(Member member, String keyword);

    /** 마이 키워드 목록 - 회원의 구독 전체(최신 등록 순), 카테고리 함께 로딩 */
    @EntityGraph(attributePaths = "categories")
    List<KeywordSubscription> findByMemberOrderByIdDesc(Member member);

    /** 소유권 검증 + 수정/삭제용 - id 와 회원으로 동시 조회 */
    @EntityGraph(attributePaths = "categories")
    Optional<KeywordSubscription> findByIdAndMember(Long id, Member member);

    /** 방송형 카테고리(예: 학식) 구독 전체 - 회원 함께 로딩(키워드 무관 발송용, on 상태만) */
    @Query("select distinct ks from KeywordSubscription ks "
            + "join fetch ks.member "
            + "join ks.categories c where c = :category and ks.enabled = true")
    List<KeywordSubscription> findByCategoryWithMember(@Param("category") AlarmCategory category);
}
