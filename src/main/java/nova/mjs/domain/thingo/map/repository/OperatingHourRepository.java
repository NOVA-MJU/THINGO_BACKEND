package nova.mjs.domain.thingo.map.repository;

import nova.mjs.domain.thingo.map.entity.OperatingHour;
import nova.mjs.domain.thingo.map.entity.Pin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.Optional;

public interface OperatingHourRepository extends JpaRepository<OperatingHour, Long> {

    /** 동기화 upsert용 - 건물(핀)+요일로 단건 조회 */
    Optional<OperatingHour> findByPinAndDayOfWeek(Pin pin, DayOfWeek dayOfWeek);
}
