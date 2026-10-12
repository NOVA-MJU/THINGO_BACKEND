package nova.mjs.domain.thingo.keywordAlarm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.keywordAlarm.dto.DeviceTokenDTO;
import nova.mjs.domain.thingo.keywordAlarm.entity.DeviceToken;
import nova.mjs.domain.thingo.keywordAlarm.repository.DeviceTokenRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * FCM 기기 토큰 서비스.
 *
 * 등록은 토큰 기준 멱등 upsert: 같은 토큰이 다른 계정에 묶여 있으면 현재 회원으로 재배정한다.
 * 비로그인 등록은 토큰을 회원에게서 떼어 기기(게스트) 소유로 돌린다 - 앱이 로그아웃 후 이 경로로 다시 등록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final MemberRepository memberRepository;

    /**
     * 로그인 회원의 기기 토큰을 등록(멱등)한다.
     *
     * @param deviceId 서버 발급 기기 id (헤더가 없거나 유효하지 않으면 null - 구버전 앱 호환)
     */
    @Transactional
    public void register(String email, UUID deviceId, DeviceTokenDTO.Request.Register request) {
        // 1. 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);

        // 2. 토큰 기준 upsert
        deviceTokenRepository.findByFcmToken(request.getFcmToken())
                .ifPresentOrElse(
                        existing -> {
                            // 같은 토큰이 다른 회원/게스트 소유면 현재 회원으로 재배정, 같으면 사실상 no-op(플랫폼만 갱신)
                            existing.reassignTo(member, deviceId, request.getPlatform());
                            log.debug("기기 토큰 재배정/갱신 - email={}", email);
                        },
                        () -> {
                            deviceTokenRepository.save(
                                    DeviceToken.of(member, deviceId, request.getFcmToken(), request.getPlatform()));
                            log.debug("기기 토큰 신규 등록 - email={}", email);
                        }
                );
    }

    /**
     * 비로그인 기기의 토큰을 등록(멱등)한다.
     * 회원 소유였던 토큰이면 회원에게서 떼어낸다(로그아웃한 기기로 회원 알림이 가지 않도록).
     */
    @Transactional
    public void registerGuest(UUID deviceId, DeviceTokenDTO.Request.Register request) {
        deviceTokenRepository.findByFcmToken(request.getFcmToken())
                .ifPresentOrElse(
                        existing -> {
                            existing.detachToGuest(deviceId, request.getPlatform());
                            log.debug("기기 토큰 게스트 전환/갱신 - deviceId={}", deviceId);
                        },
                        () -> {
                            deviceTokenRepository.save(
                                    DeviceToken.ofGuest(deviceId, request.getFcmToken(), request.getPlatform()));
                            log.debug("게스트 기기 토큰 신규 등록 - deviceId={}", deviceId);
                        }
                );
    }

    /**
     * 로그아웃 등으로 현재 회원의 기기 토큰을 제거한다.
     */
    @Transactional
    public void delete(String email, String fcmToken) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);

        // 본인 소유 토큰만 삭제
        deviceTokenRepository.findByFcmToken(fcmToken)
                .filter(token -> token.isOwnedBy(member))
                .ifPresent(deviceTokenRepository::delete);
    }

    /**
     * FCM 발송 실패로 무효/만료 판정된 토큰을 정리한다(소유자 무관).
     * FcmSender 에서 비동기로 호출된다.
     */
    @Transactional
    public void deleteByToken(String fcmToken) {
        deviceTokenRepository.deleteByFcmToken(fcmToken);
        log.info("무효 FCM 토큰 정리 - token(masked)={}", mask(fcmToken));
    }

    private String mask(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
