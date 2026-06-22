package nova.mjs.domain.thingo.map.repository;

import nova.mjs.domain.thingo.map.entity.Floor;
import nova.mjs.domain.thingo.map.entity.Pin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FloorRepository extends JpaRepository<Floor, Long> {

    /** 동기화 upsert용 - 건물+층라벨로 단건 조회 */
    Optional<Floor> findByBuildingAndLabel(Pin building, String label);
}
