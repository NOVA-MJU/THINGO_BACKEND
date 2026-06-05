package nova.mjs.domain.thingo.map.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.map.config.BusStationCatalog;
import nova.mjs.domain.thingo.map.entity.BusFavorite;
import nova.mjs.domain.thingo.map.repository.BusFavoriteRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 버스 노선 즐겨찾기 서비스
 *
 * - 토글 방식: 즐겨찾기가 있으면 삭제(false), 없으면 추가(true)
 * - 프론트는 정류장 키(A/B)만 전달하고 실제 arsId는 백엔드 카탈로그에서 해석한다
 * - 즐겨찾기 단위는 (회원, 정류장 arsId, 노선 routeName)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusFavoriteService {

    private final BusFavoriteRepository busFavoriteRepository;
    private final MemberRepository memberRepository;
    private final BusStationCatalog stationCatalog;

    /**
     * 특정 정류장의 특정 노선 즐겨찾기를 토글한다.
     *
     * @param email      현재 로그인 회원 이메일
     * @param stationKey 정류장 선택 키 ("A" 또는 "B")
     * @param routeName  버스 노선 번호
     * @return 토글 결과 - true: 즐겨찾기 추가됨, false: 즐겨찾기 해제됨
     */
    @Transactional
    public boolean toggleFavorite(String email, String stationKey, String routeName) {
        // 0. 정류장 키 → 실제 arsId 해석 (잘못된 키면 BUS_STATION_NOT_FOUND)
        String arsId = stationCatalog.resolve(stationKey).getArsId();

        // 1. 회원 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);

        // 2. 기존 즐겨찾기 존재 여부 확인
        Optional<BusFavorite> existing =
                busFavoriteRepository.findByMemberAndArsIdAndRouteName(member, arsId, routeName);

        // 3. 있으면 삭제(해제), 없으면 추가(등록)
        if (existing.isPresent()) {
            busFavoriteRepository.delete(existing.get());
            log.debug("버스 즐겨찾기 해제 - email={}, arsId={}, route={}", email, arsId, routeName);
            return false;
        }

        busFavoriteRepository.save(BusFavorite.of(member, arsId, routeName));
        log.debug("버스 즐겨찾기 등록 - email={}, arsId={}, route={}", email, arsId, routeName);
        return true;
    }
}
