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
import java.util.Set;
import java.util.UUID;

public interface KeywordSubscriptionRepository
        extends JpaRepository<KeywordSubscription, Long>, KeywordSubscriptionQueryRepository {

    /** 중복 등록 방지 - 회원이 같은 키워드를 이미 등록했는지 */
    boolean existsByMemberAndKeyword(Member member, String keyword);

    /** 마이 키워드 목록 - 회원의 구독 전체(최신 등록 순), 카테고리 함께 로딩 */
    @EntityGraph(attributePaths = "categories")
    List<KeywordSubscription> findByMemberOrderByIdDesc(Member member);

    /** 마이페이지 집계용 - 활성/비활성을 포함한 키워드 알림 설정 수 */
    long countByMember(Member member);

    /** 소유권 검증 + 수정/삭제용 - id 와 회원으로 동시 조회 */
    @EntityGraph(attributePaths = "categories")
    Optional<KeywordSubscription> findByIdAndMember(Long id, Member member);

    // 비로그인 기기 소유 구독 (위 회원 메서드와 짝)

    boolean existsByDeviceIdAndKeyword(UUID deviceId, String keyword);

    @EntityGraph(attributePaths = "categories")
    List<KeywordSubscription> findByDeviceIdOrderByIdDesc(UUID deviceId);

    long countByDeviceId(UUID deviceId);

    @EntityGraph(attributePaths = "categories")
    Optional<KeywordSubscription> findByIdAndDeviceId(Long id, UUID deviceId);

    /**
     * 방송형 카테고리(예: 학식) 구독 전체 - 회원 함께 로딩(키워드 무관 발송용, on 상태만).
     * 비로그인 기기 구독(member 없음)도 받아야 하므로 left join.
     */
    @Query("select distinct ks from KeywordSubscription ks "
            + "left join fetch ks.member "
            + "join ks.categories c where c = :category and ks.enabled = true")
    List<KeywordSubscription> findByCategoryWithMember(@Param("category") AlarmCategory category);

    /**
     * 분류된 공지 Topic과 일치하는 표준 Topic 구독 조회. 부모 Topic은 공지의 확장 Topic에 포함된다.
     * ks.member.id 경로식은 inner join 이 되어 기기 구독이 빠지므로 명시적 left join 을 쓴다.
     */
    @Query("select distinct new nova.mjs.domain.thingo.keywordAlarm.repository.KeywordMatch("
            + "ks.id, m.id, ks.deviceId, ks.keyword, ks.topicId) from KeywordSubscription ks "
            + "left join ks.member m "
            + "join ks.categories c where c = :category and ks.enabled = true "
            + "and ks.topicId in :topicIds")
    List<KeywordMatch> findMatchingTopicSubscriptions(
            @Param("category") AlarmCategory category,
            @Param("topicIds") Set<String> topicIds);
}
