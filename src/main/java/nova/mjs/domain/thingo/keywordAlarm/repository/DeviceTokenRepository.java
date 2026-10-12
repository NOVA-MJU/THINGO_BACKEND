package nova.mjs.domain.thingo.keywordAlarm.repository;

import nova.mjs.domain.thingo.keywordAlarm.entity.DeviceToken;
import nova.mjs.domain.thingo.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByFcmToken(String fcmToken);

    List<DeviceToken> findByMember(Member member);

    /** 이벤트 리스너가 엔티티를 다시 로딩하지 않고 회원의 전체 기기 토큰을 찾는다. */
    List<DeviceToken> findByMember_Id(Long memberId);

    /**
     * 비로그인 기기 수신자의 토큰. 회원 소유(로그인 중)인 토큰은 제외한다 -
     * 로그인한 기기는 회원 알림만 받아야 하고, 게스트 구독은 로그인 시 회원으로 병합된다.
     */
    List<DeviceToken> findByDeviceIdAndMemberIsNull(UUID deviceId);

    void deleteByFcmToken(String fcmToken);
}
