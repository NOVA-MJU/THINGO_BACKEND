package nova.mjs.domain.thingo.map.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nova.mjs.util.entity.BaseEntity;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * 건물의 요일별 운영시간 1건.
 *
 * 운영시간/운영상태는 건물에만 부여한다. 장소(비건물)는 운영시간을 갖지 않고 추가정보(infoText)만 제공한다.
 * 한 건물이 요일마다 한 행씩 갖는다(월~일 최대 7행). 현재 운영 상태(운영중/곧종료 등)는
 * 저장하지 않고 이 운영시간 + 현재 시각으로 매 요청마다 계산한다.
 *
 * [자정 넘김 처리]
 * 새벽까지 운영하는 곳(예: 05:00~다음날 00:00)은 종료 시각을 23:59로 근사 저장한다.
 *
 * [24시간/휴무]
 * - always24h=true 면 종일 운영 (open/close 무시)
 * - closed=true 면 해당 요일 휴무
 */
@Entity
@Table(
        name = "map_operating_hour",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_map_operating_hour_pin_day",
                columnNames = {"pin_id", "day_of_week"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatingHour extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_operating_hour_id")
    private Long id;

    /** 운영시간이 속한 건물 (Pin, type=BUILDING) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pin_id", nullable = false)
    private Pin pin;

    /** 요일 */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    /** 운영 시작 시각. 24시간/휴무면 null */
    @Column(name = "open_time")
    private LocalTime openTime;

    /** 운영 종료 시각 (자정 넘김은 23:59로 근사). 24시간/휴무면 null */
    @Column(name = "close_time")
    private LocalTime closeTime;

    /** 24시간 운영 여부 */
    @Column(name = "always_24h", nullable = false)
    private boolean always24h;

    /** 해당 요일 휴무 여부 */
    @Column(name = "closed", nullable = false)
    private boolean closed;

    /** 부가 안내 (예: "00:00~05:00 학생증 태그 시 출입"). 없으면 null */
    @Column(name = "note")
    private String note;

    @Builder(access = AccessLevel.PRIVATE)
    private OperatingHour(Pin pin, DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime,
                          boolean always24h, boolean closed, String note) {
        this.pin = pin;
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.always24h = always24h;
        this.closed = closed;
        this.note = note;
    }

    /** 일반 운영시간 (open~close) */
    public static OperatingHour ofOpen(Pin pin, DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime, String note) {
        return OperatingHour.builder()
                .pin(pin)
                .dayOfWeek(dayOfWeek)
                .openTime(openTime)
                .closeTime(closeTime)
                .always24h(false)
                .closed(false)
                .note(note)
                .build();
    }

    /** 24시간 운영 */
    public static OperatingHour ofAlwaysOpen(Pin pin, DayOfWeek dayOfWeek, String note) {
        return OperatingHour.builder()
                .pin(pin)
                .dayOfWeek(dayOfWeek)
                .always24h(true)
                .closed(false)
                .note(note)
                .build();
    }

    /** 휴무 */
    public static OperatingHour ofClosed(Pin pin, DayOfWeek dayOfWeek) {
        return OperatingHour.builder()
                .pin(pin)
                .dayOfWeek(dayOfWeek)
                .always24h(false)
                .closed(true)
                .build();
    }
}
