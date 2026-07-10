package nova.mjs.domain.thingo.block.service;

import nova.mjs.domain.thingo.block.entity.Block;
import nova.mjs.domain.thingo.block.exception.BlockNotFoundException;
import nova.mjs.domain.thingo.block.exception.SelfBlockNotAllowedException;
import nova.mjs.domain.thingo.block.repository.BlockRepository;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BlockServiceImplTest {

    @Mock
    private BlockRepository blockRepository;
    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private BlockServiceImpl blockService;

    private static final String ME_EMAIL = "me@mju.ac.kr";

    private Member 회원(Long id, UUID uuid, String email) {
        return Member.builder().id(id).uuid(uuid).email(email).nickname("nick" + id).name("name" + id).build();
    }

    @Test
    @DisplayName("정상 차단 시 관계를 저장한다")
    void should_저장_when_정상차단() {
        // given
        Member me = 회원(1L, UUID.randomUUID(), ME_EMAIL);
        UUID targetUuid = UUID.randomUUID();
        Member target = 회원(2L, targetUuid, "t@mju.ac.kr");
        given(memberRepository.findByEmail(ME_EMAIL)).willReturn(Optional.of(me));
        given(memberRepository.findByUuid(targetUuid)).willReturn(Optional.of(target));
        given(blockRepository.existsByBlockerAndBlocked(me, target)).willReturn(false);

        // when
        blockService.block(ME_EMAIL, targetUuid);

        // then
        verify(blockRepository).save(any(Block.class));
    }

    @Test
    @DisplayName("자기 자신은 차단할 수 없다")
    void should_throwException_when_자기차단() {
        // given
        UUID myUuid = UUID.randomUUID();
        Member me = 회원(1L, myUuid, ME_EMAIL);
        given(memberRepository.findByEmail(ME_EMAIL)).willReturn(Optional.of(me));
        given(memberRepository.findByUuid(myUuid)).willReturn(Optional.of(me));

        // when & then
        assertThatThrownBy(() -> blockService.block(ME_EMAIL, myUuid))
                .isInstanceOf(SelfBlockNotAllowedException.class);
        verify(blockRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 차단한 상대면 저장하지 않는다(멱등)")
    void should_저장안함_when_이미차단() {
        // given
        Member me = 회원(1L, UUID.randomUUID(), ME_EMAIL);
        UUID targetUuid = UUID.randomUUID();
        Member target = 회원(2L, targetUuid, "t@mju.ac.kr");
        given(memberRepository.findByEmail(ME_EMAIL)).willReturn(Optional.of(me));
        given(memberRepository.findByUuid(targetUuid)).willReturn(Optional.of(target));
        given(blockRepository.existsByBlockerAndBlocked(me, target)).willReturn(true);

        // when
        blockService.block(ME_EMAIL, targetUuid);

        // then
        verify(blockRepository, never()).save(any());
    }

    @Test
    @DisplayName("차단 해제 시 관계가 없으면 예외를 던진다")
    void should_throwException_when_해제할차단없음() {
        // given
        Member me = 회원(1L, UUID.randomUUID(), ME_EMAIL);
        UUID targetUuid = UUID.randomUUID();
        Member target = 회원(2L, targetUuid, "t@mju.ac.kr");
        given(memberRepository.findByEmail(ME_EMAIL)).willReturn(Optional.of(me));
        given(memberRepository.findByUuid(targetUuid)).willReturn(Optional.of(target));
        given(blockRepository.findByBlockerAndBlocked(me, target)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> blockService.unblock(ME_EMAIL, targetUuid))
                .isInstanceOf(BlockNotFoundException.class);
    }

    @Test
    @DisplayName("숨김 대상은 내가 차단한 사용자와 나를 차단한 사용자의 합집합이다")
    void should_합집합반환_when_숨김대상조회() {
        // given
        given(blockRepository.findBlockedMemberIds(1L)).willReturn(List.of(2L, 3L));
        given(blockRepository.findBlockerMemberIds(1L)).willReturn(List.of(3L, 4L));

        // when
        Set<Long> hidden = blockService.getHiddenMemberIds(1L);

        // then
        assertThat(hidden).containsExactlyInAnyOrder(2L, 3L, 4L);
    }

    @Test
    @DisplayName("뷰어가 없으면 숨김 대상은 비어 있다")
    void should_빈집합_when_뷰어없음() {
        // when
        Set<Long> hidden = blockService.getHiddenMemberIds(null);

        // then
        assertThat(hidden).isEmpty();
        verify(blockRepository, never()).findBlockedMemberIds(any());
    }
}
