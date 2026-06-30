package nova.mjs.domain.thingo.broadcast.repository;

import nova.mjs.domain.thingo.broadcast.entity.Broadcast;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {
    Optional<Broadcast> findByVideoId(String videoId);

    // 전체(방송국+공식) 조회는 상속된 findAll(pageable) 사용. 출처 필터는 아래 메서드.
    Page<Broadcast> findBySource(Broadcast.Source source, Pageable pageable);

    // 출처별 정리 삭제 (다른 출처 데이터를 건드리지 않도록 source 로 스코프)
    void deleteBySourceAndPublishedAtBefore(Broadcast.Source source, LocalDateTime boundary);
    void deleteBySourceAndLastSyncedAtBefore(Broadcast.Source source, LocalDateTime boundary);
}
