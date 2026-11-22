package nova.mjs.domain.thingo.map.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 네이버 지도 글자(가게 이름·좌표)와 사용자가 고른 대동명지도 가게 종류(daedong-cafe 등).
 */
public record SymbolPlaceCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotBlank String categoryCode
) {
}
