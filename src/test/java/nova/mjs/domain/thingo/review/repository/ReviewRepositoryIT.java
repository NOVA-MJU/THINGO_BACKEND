package nova.mjs.domain.thingo.review.repository;

import nova.mjs.domain.thingo.ElasticSearch.indexing.publisher.SearchIndexPublisher;
import nova.mjs.domain.thingo.department.entity.enumList.College;
import nova.mjs.domain.thingo.map.entity.Category;
import nova.mjs.domain.thingo.map.entity.CategoryGroup;
import nova.mjs.domain.thingo.map.entity.CategoryResultType;
import nova.mjs.domain.thingo.map.entity.Pin;
import nova.mjs.domain.thingo.member.entity.Member;
import nova.mjs.domain.thingo.review.entity.Review;
import nova.mjs.domain.thingo.review.entity.ReviewKeyword;
import nova.mjs.domain.thingo.review.entity.ReviewLike;
import nova.mjs.domain.thingo.review.entity.ReviewMediaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리뷰 리포지토리 통합 테스트.
 * - 키워드(ElementCollection) + 미디어(순서 보존) round-trip
 * - 장소별 최신순 페이지 / 차단 사용자 제외 조회
 * - 좋아요 원자적 증감 + (member, review) 유니크 제약
 */
@Testcontainers
@DataJpaTest(excludeAutoConfiguration = {
        ElasticsearchRestClientAutoConfiguration.class,
        ElasticsearchDataAutoConfiguration.class,
        ElasticsearchRepositoriesAutoConfiguration.class,
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        MailSenderAutoConfiguration.class,
        SecurityAutoConfiguration.class
})
@EnableAutoConfiguration
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class ReviewRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewLikeRepository reviewLikeRepository;

    @Autowired
    private TestEntityManager em;

    /** JPA 엔티티 리스너가 요구하는 의존성 충족용 */
    @MockBean
    private SearchIndexPublisher searchIndexPublisher;

    // ===== 픽스처 헬퍼 =====

    private Member 영속회원(String email) {
        Member member = Member.builder()
                .uuid(UUID.randomUUID())
                .role(Member.Role.USER)
                .name("테스터")
                .nickname("길동")
                .email(email)
                .password("encoded")
                .college(College.AI_SOFTWARE)
                .build();
        return em.persistAndFlush(member);
    }

    private Pin 영속장소(String code, String name) {
        CategoryGroup group = em.persistAndFlush(CategoryGroup.of("food", "식사 (F&B)", 1));
        Category category = em.persistAndFlush(Category.ofChip(
                "restaurant", group, "음식점", null, null, null,
                CategoryResultType.PLACE_LIST, false, 1));
        Pin pin = Pin.ofExternalPlace(code, category, name, 37.0, 127.0, null, null, "서울시 어딘가");
        return em.persistAndFlush(pin);
    }

    private Review 영속리뷰(Pin pin, Member author, String content, Set<ReviewKeyword> keywords) {
        Review review = Review.create(pin, author, content, keywords);
        return em.persistAndFlush(review);
    }

    /** @CreationTimestamp 를 우회해 created_at 을 명시값으로 덮어써 정렬을 결정적으로 만든다 */
    private void createdAt덮어쓰기(Long reviewId, String isoDateTime) {
        em.getEntityManager()
                .createNativeQuery("update map_review set created_at = cast(:ts as timestamp) where map_review_id = :id")
                .setParameter("ts", isoDateTime)
                .setParameter("id", reviewId)
                .executeUpdate();
    }

    // ===== 테스트 =====

    @Test
    @DisplayName("키워드 집합과 미디어 순서가 그대로 저장·조회된다")
    void should_round_trip_키워드_미디어() {
        // given
        Member author = 영속회원("a@mju.ac.kr");
        Pin pin = 영속장소("p-1", "학식당");
        Review review = Review.create(pin, author, "가성비 최고", Set.of(ReviewKeyword.TASTY, ReviewKeyword.VALUE));
        review.addMedia("https://thingo.kr/1.png", ReviewMediaType.IMAGE);
        review.addMedia("https://thingo.kr/2.mp4", ReviewMediaType.VIDEO);
        UUID uuid = review.getUuid();
        em.persistAndFlush(review);
        em.clear();

        // when
        Review found = reviewRepository.findByUuid(uuid).orElseThrow();

        // then
        assertThat(found.getContent()).isEqualTo("가성비 최고");
        assertThat(found.getKeywords())
                .containsExactlyInAnyOrder(ReviewKeyword.TASTY, ReviewKeyword.VALUE);
        assertThat(found.getMedia()).hasSize(2);
        assertThat(found.getMedia().get(0).getSortOrder()).isEqualTo(0);
        assertThat(found.getMedia().get(0).getMediaType()).isEqualTo(ReviewMediaType.IMAGE);
        assertThat(found.getMedia().get(1).getSortOrder()).isEqualTo(1);
        assertThat(found.getMedia().get(1).getMediaType()).isEqualTo(ReviewMediaType.VIDEO);
    }

    @Test
    @DisplayName("장소별 리뷰가 최신순(createdAt DESC)으로 정렬된다")
    void should_정렬_최신순() {
        // given - 같은 장소에 3개, created_at 을 결정적으로 부여
        Member author = 영속회원("b@mju.ac.kr");
        Pin pin = 영속장소("p-2", "카페");
        Review r1 = 영속리뷰(pin, author, "오래된 리뷰", Set.of(ReviewKeyword.KIND));
        Review r2 = 영속리뷰(pin, author, "최신 리뷰", Set.of(ReviewKeyword.COZY));
        Review r3 = 영속리뷰(pin, author, "중간 리뷰", Set.of(ReviewKeyword.FOCUS));
        createdAt덮어쓰기(r1.getId(), "2026-07-01T10:00:00");
        createdAt덮어쓰기(r2.getId(), "2026-07-03T10:00:00");
        createdAt덮어쓰기(r3.getId(), "2026-07-02T10:00:00");
        em.clear();

        // when
        List<Review> result = reviewRepository
                .findByPin_IdAndHiddenFalseOrderByCreatedAtDesc(pin.getId(), PageRequest.of(0, 10))
                .getContent();

        // then - r2(7/3) > r3(7/2) > r1(7/1)
        assertThat(result).extracting(Review::getContent)
                .containsExactly("최신 리뷰", "중간 리뷰", "오래된 리뷰");
    }

    @Test
    @DisplayName("차단 사용자(author id)의 리뷰는 목록에서 제외된다")
    void should_제외_차단사용자() {
        // given
        Member visible = 영속회원("visible@mju.ac.kr");
        Member blocked = 영속회원("blocked@mju.ac.kr");
        Pin pin = 영속장소("p-3", "분식집");
        영속리뷰(pin, visible, "보이는 리뷰", Set.of(ReviewKeyword.TASTY));
        영속리뷰(pin, blocked, "숨겨질 리뷰", Set.of(ReviewKeyword.VALUE));
        em.clear();

        // when
        List<Review> result = reviewRepository
                .findByPin_IdAndHiddenFalseAndAuthor_IdNotInOrderByCreatedAtDesc(
                        pin.getId(), List.of(blocked.getId()), PageRequest.of(0, 10))
                .getContent();

        // then
        assertThat(result).extracting(Review::getContent).containsExactly("보이는 리뷰");
    }

    @Test
    @DisplayName("자동 숨김(hidden) 리뷰는 목록에서 제외되고 운영자 큐에서 조회된다")
    void should_제외_hidden_리뷰() {
        // given
        Member author = 영속회원("hid@mju.ac.kr");
        Pin pin = 영속장소("p-hidden", "숨김카페");
        영속리뷰(pin, author, "보이는 리뷰", Set.of(ReviewKeyword.KIND));
        Review hidden = 영속리뷰(pin, author, "숨겨진 리뷰", Set.of(ReviewKeyword.COZY));
        hidden.hideByReport();
        em.flush();
        em.clear();

        // when
        List<Review> list = reviewRepository
                .findByPin_IdAndHiddenFalseOrderByCreatedAtDesc(pin.getId(), PageRequest.of(0, 10))
                .getContent();
        List<Review> hiddenQueue = reviewRepository.findByHiddenTrueOrderByCreatedAtDesc();

        // then
        assertThat(list).extracting(Review::getContent).containsExactly("보이는 리뷰");
        assertThat(hiddenQueue).extracting(Review::getContent).containsExactly("숨겨진 리뷰");
    }

    @Test
    @DisplayName("좋아요 수가 원자적으로 증감하며 0 미만으로 내려가지 않는다")
    void should_좋아요_원자적_증감() {
        // given
        Member author = 영속회원("c@mju.ac.kr");
        Pin pin = 영속장소("p-4", "고깃집");
        Review review = 영속리뷰(pin, author, "리뷰", Set.of(ReviewKeyword.GENEROUS));
        UUID uuid = review.getUuid();
        em.clear();

        // when & then
        reviewRepository.increaseLikeCount(uuid);
        reviewRepository.increaseLikeCount(uuid);
        assertThat(reviewRepository.findLikeCount(uuid)).isEqualTo(2);

        reviewRepository.decreaseLikeCount(uuid);
        assertThat(reviewRepository.findLikeCount(uuid)).isEqualTo(1);

        reviewRepository.decreaseLikeCount(uuid);
        reviewRepository.decreaseLikeCount(uuid); // 0에서 한 번 더 → 0 유지
        assertThat(reviewRepository.findLikeCount(uuid)).isEqualTo(0);
    }

    @Test
    @DisplayName("같은 회원이 같은 리뷰에 좋아요를 중복 저장하면 제약 위반")
    void should_violate_unique_when_중복좋아요() {
        // given
        Member member = 영속회원("d@mju.ac.kr");
        Pin pin = 영속장소("p-5", "주점");
        Review review = 영속리뷰(pin, member, "리뷰", Set.of(ReviewKeyword.GOOD_VIBE));
        reviewLikeRepository.saveAndFlush(new ReviewLike(member, review));

        // when & then
        assertThatThrownBy(() ->
                reviewLikeRepository.saveAndFlush(new ReviewLike(member, review)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findLikedReviewUuids 는 회원이 좋아요한 리뷰 uuid만 반환한다")
    void should_return_liked_uuids() {
        // given
        Member member = 영속회원("e@mju.ac.kr");
        Pin pin = 영속장소("p-6", "라멘집");
        Review liked = 영속리뷰(pin, member, "좋아요한 리뷰", Set.of(ReviewKeyword.TASTY));
        Review notLiked = 영속리뷰(pin, member, "안 누른 리뷰", Set.of(ReviewKeyword.FRESH));
        reviewLikeRepository.saveAndFlush(new ReviewLike(member, liked));
        em.clear();

        // when
        List<UUID> result = reviewLikeRepository.findLikedReviewUuids(
                member.getId(), List.of(liked.getUuid(), notLiked.getUuid()));

        // then
        assertThat(result).containsExactly(liked.getUuid());
    }
}
