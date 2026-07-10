package nova.mjs.domain.thingo.block.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nova.mjs.domain.thingo.block.dto.BlockDTO;
import nova.mjs.domain.thingo.block.entity.Block;
import nova.mjs.domain.thingo.block.exception.BlockNotFoundException;
import nova.mjs.domain.thingo.block.exception.SelfBlockNotAllowedException;
import nova.mjs.domain.thingo.block.repository.BlockRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.exception.MemberNotFoundException;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockServiceImpl implements BlockService, BlockQueryService {

    private final BlockRepository blockRepository;
    private final MemberRepository memberRepository;

    /**
     * 차단 처리.
     *
     * 1. 차단 실행자/대상 조회
     * 2. 자기 자신 차단 방지
     * 3. 이미 차단한 상대면 멱등 종료(중복 저장 금지)
     * 4. 차단 관계 저장
     */
    @Override
    @Transactional
    public void block(String blockerEmail, UUID targetMemberUuid) {
        Member blocker = getMemberByEmail(blockerEmail);
        Member target = getMemberByUuid(targetMemberUuid);

        if (Objects.equals(blocker.getId(), target.getId())) {
            throw new SelfBlockNotAllowedException();
        }

        if (blockRepository.existsByBlockerAndBlocked(blocker, target)) {
            log.debug("이미 차단된 사용자 - blocker: {}, targetUuid: {}", blockerEmail, targetMemberUuid);
            return;
        }

        blockRepository.save(Block.of(blocker, target));
        log.info("사용자 차단 완료 - blocker: {}, targetUuid: {}", blockerEmail, targetMemberUuid);
    }

    /**
     * 차단 해제.
     * 차단 관계가 없으면 예외.
     */
    @Override
    @Transactional
    public void unblock(String blockerEmail, UUID targetMemberUuid) {
        Member blocker = getMemberByEmail(blockerEmail);
        Member target = getMemberByUuid(targetMemberUuid);

        Block block = blockRepository.findByBlockerAndBlocked(blocker, target)
                .orElseThrow(BlockNotFoundException::new);

        blockRepository.delete(block);
        log.info("사용자 차단 해제 완료 - blocker: {}, targetUuid: {}", blockerEmail, targetMemberUuid);
    }

    /**
     * 내가 차단한 사용자 목록(최근 차단순).
     */
    @Override
    public List<BlockDTO.Response.BlockedMember> getBlockedMembers(String blockerEmail) {
        Member blocker = getMemberByEmail(blockerEmail);
        return blockRepository.findAllByBlockerOrderByCreatedAtDesc(blocker).stream()
                .map(BlockDTO.Response.BlockedMember::from)
                .toList();
    }

    /**
     * 양방향 숨김 대상 member id 집합.
     * 내가 차단한 사용자 + 나를 차단한 사용자.
     */
    @Override
    public Set<Long> getHiddenMemberIds(Long viewerMemberId) {
        if (viewerMemberId == null) {
            return Set.of();
        }
        Set<Long> hidden = new HashSet<>(blockRepository.findBlockedMemberIds(viewerMemberId));
        hidden.addAll(blockRepository.findBlockerMemberIds(viewerMemberId));
        return hidden;
    }

    private Member getMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);
    }

    private Member getMemberByUuid(UUID uuid) {
        return memberRepository.findByUuid(uuid)
                .orElseThrow(MemberNotFoundException::new);
    }
}
