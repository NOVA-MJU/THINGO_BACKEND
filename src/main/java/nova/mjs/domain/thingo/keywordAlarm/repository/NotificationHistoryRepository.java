package nova.mjs.domain.thingo.keywordAlarm.repository;

import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    /**
     * 알림 화면 정렬 규칙: 미읽음 최신순 -> 읽음 최신순.
     * 같은 시각에 생성된 경우에도 순서가 흔들리지 않도록 id를 마지막 정렬 키로 사용한다.
     */
    @Query("""
        select n
        from NotificationHistory n
        where n.member = :member
        order by n.read asc, n.sentAt desc, n.id desc
    """)
    Page<NotificationHistory> findInbox(@Param("member") Member member, Pageable pageable);

    Optional<NotificationHistory> findByIdAndMember(Long id, Member member);

    Optional<NotificationHistory> findByMemberAndSearchIndexId(Member member, String searchIndexId);

    long countByMemberAndReadFalse(Member member);

    /** 교차게시 중복 판정용 - 회원이 최근(since 이후) 받은 알림 제목 */
    @Query("select n.title from NotificationHistory n where n.member.id = :memberId and n.sentAt >= :since")
    List<String> findRecentTitles(@Param("memberId") Long memberId, @Param("since") Instant since);

    /** 읽지 않은 알림 일괄 읽음 처리, 변경 건수 반환 */
    @Modifying(clearAutomatically = true)
    @Query("update NotificationHistory n set n.read = true where n.member = :member and n.read = false")
    int markAllAsRead(@Param("member") Member member);
}
