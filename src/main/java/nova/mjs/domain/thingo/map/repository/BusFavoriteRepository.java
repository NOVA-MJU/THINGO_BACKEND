package nova.mjs.domain.thingo.map.repository;

import nova.mjs.domain.thingo.map.entity.BusFavorite;
import nova.mjs.domain.thingo.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusFavoriteRepository extends JpaRepository<BusFavorite, Long> {

    /** 토글용: 특정 회원의 (정류장, 노선) 즐겨찾기 단건 조회 */
    Optional<BusFavorite> findByMemberAndArsIdAndRouteName(Member member, String arsId, String routeName);

    /** 도착 정보 마킹용: 특정 회원이 해당 정류장에서 즐겨찾기한 노선 번호 목록 */
    @Query("SELECT bf.routeName FROM BusFavorite bf WHERE bf.member = :member AND bf.arsId = :arsId")
    List<String> findRouteNamesByMemberAndArsId(@Param("member") Member member, @Param("arsId") String arsId);
}
