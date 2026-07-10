package nova.mjs.domain.thingo.block.service;

import nova.mjs.domain.thingo.block.dto.BlockDTO;

import java.util.List;
import java.util.UUID;

/**
 * 차단 명령/조회 API 진입점 (컨트롤러용).
 */
public interface BlockService {

    /** 차단 (이미 차단한 상대면 멱등 처리) */
    void block(String blockerEmail, UUID targetMemberUuid);

    /** 차단 해제 */
    void unblock(String blockerEmail, UUID targetMemberUuid);

    /** 내가 차단한 사용자 목록 */
    List<BlockDTO.Response.BlockedMember> getBlockedMembers(String blockerEmail);
}
