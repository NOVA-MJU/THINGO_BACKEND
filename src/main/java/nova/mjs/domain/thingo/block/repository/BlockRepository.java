package nova.mjs.domain.thingo.block.repository;

import nova.mjs.domain.thingo.block.entity.Block;
import nova.mjs.domain.thingo.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerAndBlocked(Member blocker, Member blocked);

    Optional<Block> findByBlockerAndBlocked(Member blocker, Member blocked);

    /** 내가 차단한 목록 (최근 차단순) */
    List<Block> findAllByBlockerOrderByCreatedAtDesc(Member blocker);

    /** 내가 차단한 사용자들의 member id */
    @Query("select b.blocked.id from Block b where b.blocker.id = :memberId")
    List<Long> findBlockedMemberIds(@Param("memberId") Long memberId);

    /** 나를 차단한 사용자들의 member id */
    @Query("select b.blocker.id from Block b where b.blocked.id = :memberId")
    List<Long> findBlockerMemberIds(@Param("memberId") Long memberId);
}
