package nova.mjs.domain.thingo.map.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.entity.PinFavorite;
import nova.mjs.domain.thingo.map.exception.PinNotFoundException;
import nova.mjs.domain.thingo.map.repository.PinFavoriteRepository;
import nova.mjs.domain.thingo.map.repository.PinRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 핀(건물/장소) 즐겨찾기 서비스.
 *
 * 토글 방식: 즐겨찾기가 있으면 해제(false), 없으면 추가(true).
 * 건물·장소 구분 없이 핀 ID 하나로 다룬다(Pin 통합).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PinFavoriteService {

    private final PinFavoriteRepository pinFavoriteRepository;
    private final PinRepository pinRepository;
    private final MemberRepository memberRepository;

    /**
     * 특정 핀의 즐겨찾기를 토글한다.
     *
     * @param email 현재 로그인 회원 이메일
     * @param pinId 건물/장소 핀 ID
     * @return true: 즐겨찾기 추가됨, false: 즐겨찾기 해제됨
     */
    @Transactional
    public boolean toggleFavorite(String email, Long pinId) {
        // 1. 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);

        // 2. 핀 존재 확인
        Pin pin = pinRepository.findById(pinId)
                .orElseThrow(PinNotFoundException::new);

        // 3. 기존 즐겨찾기 있으면 해제, 없으면 등록
        Optional<PinFavorite> existing = pinFavoriteRepository.findByMemberAndPin(member, pin);
        if (existing.isPresent()) {
            pinFavoriteRepository.delete(existing.get());
            log.debug("핀 즐겨찾기 해제 - email={}, pinId={}", email, pinId);
            return false;
        }

        pinFavoriteRepository.save(PinFavorite.of(member, pin));
        log.debug("핀 즐겨찾기 등록 - email={}, pinId={}", email, pinId);
        return true;
    }
}
