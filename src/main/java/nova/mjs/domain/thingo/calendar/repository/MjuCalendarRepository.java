package nova.mjs.domain.thingo.calendar.repository;

import nova.mjs.domain.thingo.calendar.entity.MjuCalendar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MjuCalendarRepository extends JpaRepository<MjuCalendar, Long> {

    @Query("""
        select c
        from MjuCalendar c
        where c.startDate <= :monthEndDate
          and c.endDate   >= :monthStartDate
        order by c.startDate asc, c.endDate asc, c.id asc
    """)
    List<MjuCalendar> findMonthlySchedule (
            @Param("monthStartDate") LocalDate monthStartDate,
            @Param("monthEndDate") LocalDate monthEndDate
    );

    Page<MjuCalendar> findByYear(int year, Pageable pageable);

    // 디데이용: 아직 끝나지 않은 일정(종료일 >= 기준일)만, 임박순 정렬
    List<MjuCalendar> findByEndDateGreaterThanEqualOrderByStartDateAscEndDateAscIdAsc(LocalDate today);
}