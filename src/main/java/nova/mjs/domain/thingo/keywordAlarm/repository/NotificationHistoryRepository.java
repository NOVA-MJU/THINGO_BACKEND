package nova.mjs.domain.thingo.keywordAlarm.repository;

import nova.mjs.domain.thingo.keywordAlarm.entity.NotificationHistory;
import nova.mjs.domain.thingo.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    Page<NotificationHistory> findByMemberOrderBySentAtDesc(Member member, Pageable pageable);

    Optional<NotificationHistory> findByIdAndMember(Long id, Member member);

    /** 읽지 않은 알림 일괄 읽음 처리, 변경 건수 반환 */
    @Modifying(clearAutomatically = true)
    @Query("update NotificationHistory n set n.read = true where n.member = :member and n.read = false")
    int markAllAsRead(@Param("member") Member member);
}
