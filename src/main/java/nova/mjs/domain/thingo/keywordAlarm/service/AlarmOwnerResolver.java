package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.keywordAlarm.entity.AlarmOwner;
import nova.mjs.domain.thingo.keywordAlarm.exception.InvalidDeviceIdException;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 키워드 알림 요청의 소유자를 결정한다(한 곳에서만 판단).
 *
 * 1. 로그인(JWT 인증) 상태면 회원.
 * 2. 아니면 X-Device-Id 헤더가 서버가 발급한 기기 id 일 때 그 기기.
 * 3. 둘 다 아니면 InvalidDeviceIdException(401).
 *
 * 로그인 상태에서는 헤더를 무시한다. 같은 요청이 회원/기기 두 소유자를 동시에 건드리면
 * 로그인 사용자의 구독이 기기 쪽에 잘못 쌓일 수 있기 때문이다(기기 -> 회원 이동은 병합 API 만 한다).
 */
@Component
@RequiredArgsConstructor
public class AlarmOwnerResolver {

    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final MemberRepository memberRepository;
    private final DeviceService deviceService;

    public AlarmOwner resolve(UserPrincipal userPrincipal, String deviceIdHeader) {
        if (userPrincipal != null) {
            return AlarmOwner.ofMember(findMember(userPrincipal.getUsername()));
        }
        return AlarmOwner.ofDevice(requireDevice(deviceIdHeader));
    }

    /** 서버가 발급한 기기 id 만 통과시킨다. */
    public UUID requireDevice(String deviceIdHeader) {
        return deviceService.findIssued(deviceIdHeader)
                .orElseThrow(InvalidDeviceIdException::new);
    }

    public Member findMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);
    }
}
