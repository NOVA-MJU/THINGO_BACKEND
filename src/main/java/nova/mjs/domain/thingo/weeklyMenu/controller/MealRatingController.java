package nova.mjs.domain.thingo.weeklyMenu.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.weeklyMenu.DTO.MealRatingDTO;
import nova.mjs.domain.thingo.weeklyMenu.service.MealRatingService;
import nova.mjs.util.response.ApiResponse;
import nova.mjs.util.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 학식 평점 API.
 * - 조회는 비로그인 허용(로그인 시 myScore 포함), date 생략 시 오늘(KST)
 * - 평점 남기기는 로그인 필요, 오늘 끼니만 가능
 */
@RestController
@RequestMapping("/api/v1/menus/ratings")
@RequiredArgsConstructor
public class MealRatingController {

    private final MealRatingService mealRatingService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MealRatingDTO.Response.Summary>>> getRatings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        LocalDate mealDate = date != null ? date : mealRatingService.today();
        String email = userPrincipal != null ? userPrincipal.getUsername() : null;
        return ResponseEntity.ok(ApiResponse.success(mealRatingService.getSummaries(mealDate, email)));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<MealRatingDTO.Response.Summary>>> rate(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody MealRatingDTO.Request.Rate request
    ) {
        return ResponseEntity.ok(ApiResponse.success(mealRatingService.rate(userPrincipal.getUsername(), request)));
    }
}
