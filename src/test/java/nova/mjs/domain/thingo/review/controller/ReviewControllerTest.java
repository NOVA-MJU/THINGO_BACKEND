package nova.mjs.domain.thingo.review.controller;

import nova.mjs.domain.thingo.review.dto.ReviewDTO;
import nova.mjs.domain.thingo.review.service.command.ReviewCommandService;
import nova.mjs.domain.thingo.review.service.like.ReviewLikeService;
import nova.mjs.domain.thingo.review.service.query.ReviewQueryService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock private ReviewCommandService reviewCommandService;
    @Mock private ReviewQueryService reviewQueryService;
    @Mock private ReviewLikeService reviewLikeService;

    @InjectMocks private ReviewController controller;

    private final UserPrincipal principal = new UserPrincipal("e@mju.ac.kr", "USER");

    @Test
    @DisplayName("작성 요청을 서비스에 위임하고 201을 반환한다")
    void should_create_201() {
        ReviewDTO.Response.Detail detail = ReviewDTO.Response.Detail.builder().build();
        given(reviewCommandService.createReview(eq("e@mju.ac.kr"), any())).willReturn(detail);

        ResponseEntity<ApiResponse<ReviewDTO.Response.Detail>> response =
                controller.createReview(principal, new ReviewDTO.Request.Create());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData()).isEqualTo(detail);
    }

    @Test
    @DisplayName("목록 조회 시 로그인 이메일을 서비스에 전달한다")
    void should_getReviews_로그인() {
        given(reviewQueryService.getReviews(eq(1L), any(), eq("e@mju.ac.kr"))).willReturn(Page.empty());

        controller.getReviews(1L, "latest", 0, 10, principal);

        verify(reviewQueryService).getReviews(eq(1L), any(), eq("e@mju.ac.kr"));
    }

    @Test
    @DisplayName("비로그인 목록 조회 시 email은 null로 전달된다")
    void should_getReviews_비로그인() {
        given(reviewQueryService.getReviews(eq(1L), any(), isNull())).willReturn(Page.empty());

        controller.getReviews(1L, "latest", 0, 10, null);

        verify(reviewQueryService).getReviews(eq(1L), any(), isNull());
    }

    @Test
    @DisplayName("상세 조회를 서비스에 위임한다")
    void should_getReview() {
        UUID uuid = UUID.randomUUID();
        ReviewDTO.Response.Detail detail = ReviewDTO.Response.Detail.builder().build();
        given(reviewQueryService.getReview(uuid, "e@mju.ac.kr")).willReturn(detail);

        ResponseEntity<ApiResponse<ReviewDTO.Response.Detail>> response =
                controller.getReview(uuid, principal);

        assertThat(response.getBody().getData()).isEqualTo(detail);
    }

    @Test
    @DisplayName("삭제 요청을 위임하고 204를 반환한다")
    void should_delete_204() {
        UUID uuid = UUID.randomUUID();

        ResponseEntity<Void> response = controller.deleteReview(uuid, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(reviewCommandService).deleteReview("e@mju.ac.kr", uuid);
    }

    @Test
    @DisplayName("좋아요 토글을 서비스에 위임한다")
    void should_toggleLike() {
        UUID uuid = UUID.randomUUID();
        ReviewDTO.Response.LikeResult result = ReviewDTO.Response.LikeResult.builder().liked(true).likeCount(1).build();
        given(reviewLikeService.toggleLike(uuid, "e@mju.ac.kr")).willReturn(result);

        ResponseEntity<ApiResponse<ReviewDTO.Response.LikeResult>> response =
                controller.toggleLike(uuid, principal);

        assertThat(response.getBody().getData().isLiked()).isTrue();
        assertThat(response.getBody().getData().getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("미디어 스트립 조회를 위임한다")
    void should_getMediaStrip() {
        given(reviewQueryService.getMediaStrip(eq(1L), eq(5), eq("e@mju.ac.kr"))).willReturn(List.of());

        controller.getMediaStrip(1L, 5, principal);

        verify(reviewQueryService).getMediaStrip(1L, 5, "e@mju.ac.kr");
    }

    @Test
    @DisplayName("키워드 카탈로그 조회를 위임한다")
    void should_getKeywordCatalog() {
        ReviewDTO.Response.KeywordCatalog catalog = ReviewDTO.Response.KeywordCatalog.builder().fnb(true).build();
        given(reviewQueryService.getKeywordCatalog(1L)).willReturn(catalog);

        ResponseEntity<ApiResponse<ReviewDTO.Response.KeywordCatalog>> response =
                controller.getKeywordCatalog(1L);

        assertThat(response.getBody().getData().isFnb()).isTrue();
    }
}
