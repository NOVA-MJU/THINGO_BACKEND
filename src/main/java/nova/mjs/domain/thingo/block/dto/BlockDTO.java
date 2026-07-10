package nova.mjs.domain.thingo.block.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.domain.thingo.block.entity.Block;
import nova.mjs.domain.thingo.member.entity.Member;

import java.time.LocalDateTime;
import java.util.UUID;

public class BlockDTO {

    public static class Request {

        /** 차단 요청 */
        @Getter
        @NoArgsConstructor
        public static class Create {

            @NotNull(message = "차단할 사용자 식별자는 필수입니다.")
            private UUID targetMemberUuid;
        }
    }

    public static class Response {

        /** 내가 차단한 사용자 1건 */
        @Getter
        @Builder
        public static class BlockedMember {
            private UUID memberUuid;
            private String nickname;
            private String name;
            private String profileImageUrl;
            private LocalDateTime blockedAt;

            public static BlockedMember from(Block block) {
                Member blocked = block.getBlocked();
                return BlockedMember.builder()
                        .memberUuid(blocked.getUuid())
                        .nickname(blocked.getNickname())
                        .name(blocked.getName())
                        .profileImageUrl(blocked.getProfileImageUrl())
                        .blockedAt(block.getCreatedAt())
                        .build();
            }
        }
    }
}
