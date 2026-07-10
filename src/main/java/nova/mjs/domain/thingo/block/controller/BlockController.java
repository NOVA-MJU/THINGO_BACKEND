package nova.mjs.domain.thingo.block.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.block.dto.BlockDTO;
import nova.mjs.domain.thingo.block.service.BlockService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    /**
     * 사용자 차단.
     * 차단하면 상대가 작성한 게시글/댓글이 내 화면에서 즉시 숨겨진다(양방향).
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> block(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody BlockDTO.Request.Create request
    ) {
        blockService.block(userPrincipal.getUsername(), request.getTargetMemberUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success());
    }

    /**
     * 차단 해제.
     */
    @DeleteMapping("/{targetMemberUuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID targetMemberUuid
    ) {
        blockService.unblock(userPrincipal.getUsername(), targetMemberUuid);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 내가 차단한 사용자 목록 조회.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BlockDTO.Response.BlockedMember>>> getBlockedMembers(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        List<BlockDTO.Response.BlockedMember> response =
                blockService.getBlockedMembers(userPrincipal.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
