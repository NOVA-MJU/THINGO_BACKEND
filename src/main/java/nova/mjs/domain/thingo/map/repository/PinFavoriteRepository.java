package nova.mjs.domain.thingo.map.repository;

import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.map.entity.PinFavorite;
import nova.mjs.domain.thingo.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PinFavoriteRepository extends JpaRepository<PinFavorite, Long> {

    /** 토글용 - 회원이 해당 핀을 이미 즐겨찾기했는지 조회 */
    Optional<PinFavorite> findByMemberAndPin(Member member, Pin pin);

    /** 마이 즐겨찾기 리스트 - 회원의 즐겨찾기 전체 */
    List<PinFavorite> findByMember(Member member);

    /** 목록 정렬용 - 회원이 즐겨찾기한 핀 ID 집합 (즐겨찾기 마킹/상단 정렬에 사용) */
    @Query("select pf.pin.id from PinFavorite pf where pf.member = :member")
    List<Long> findFavoritePinIds(@Param("member") Member member);
}
