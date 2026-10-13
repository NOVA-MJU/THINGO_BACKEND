package nova.mjs.domain.thingo.map.dto;

import nova.mjs.domain.thingo.map.config.MapThemeCatalog;

/**
 * 테마지도 카드 (명지도 검색 화면 하단 목록).
 */
public record MapThemeResponse(String id, String title, String subtitle, String imageUrl, int placeCount) {

    public static MapThemeResponse from(MapThemeCatalog.Theme theme) {
        return new MapThemeResponse(
                theme.id(), theme.title(), theme.subtitle(), theme.imageUrl(), theme.placeCodes().size());
    }
}
