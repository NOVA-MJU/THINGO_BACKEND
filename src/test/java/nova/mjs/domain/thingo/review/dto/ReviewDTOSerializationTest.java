package nova.mjs.domain.thingo.review.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응답 DTO JSON 키 검증. 커뮤니티와 동일하게 isLiked/isMine/isFnb 형태로 나가야 한다.
 * (primitive boolean 은 Jackson 기본 규칙상 liked/mine 으로 나가므로 @JsonProperty 로 고정)
 */
class ReviewDTOSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Summary는 isLiked/isMine 키로 직렬화된다")
    void should_serialize_summary_flags() throws Exception {
        ReviewDTO.Response.Summary summary = ReviewDTO.Response.Summary.builder()
                .liked(true).mine(false).likeCount(3).build();

        String json = objectMapper.writeValueAsString(summary);

        assertThat(json).contains("\"isLiked\":true");
        assertThat(json).contains("\"isMine\":false");
    }

    @Test
    @DisplayName("Detail은 isLiked/isMine/canDelete 키로 직렬화된다")
    void should_serialize_detail_flags() throws Exception {
        ReviewDTO.Response.Detail detail = ReviewDTO.Response.Detail.builder()
                .liked(false).mine(true).canDelete(true).build();

        String json = objectMapper.writeValueAsString(detail);

        assertThat(json).contains("\"isLiked\":false");
        assertThat(json).contains("\"isMine\":true");
        assertThat(json).contains("\"canDelete\":true");
    }

    @Test
    @DisplayName("KeywordCatalog는 isFnb 키로 직렬화된다")
    void should_serialize_catalog_fnb() throws Exception {
        ReviewDTO.Response.KeywordCatalog catalog = ReviewDTO.Response.KeywordCatalog.builder()
                .fnb(true).build();

        String json = objectMapper.writeValueAsString(catalog);

        assertThat(json).contains("\"isFnb\":true");
    }

    @Test
    @DisplayName("LikeResult는 liked/likeCount 키로 직렬화된다")
    void should_serialize_like_result() throws Exception {
        ReviewDTO.Response.LikeResult result = ReviewDTO.Response.LikeResult.builder()
                .liked(true).likeCount(5).build();

        String json = objectMapper.writeValueAsString(result);

        assertThat(json).contains("\"liked\":true");
        assertThat(json).contains("\"likeCount\":5");
    }
}
