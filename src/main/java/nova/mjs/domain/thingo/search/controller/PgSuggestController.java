package nova.mjs.domain.thingo.search.controller;

import lombok.RequiredArgsConstructor;
import nova.mjs.domain.thingo.search.service.PgSuggestService;
import nova.mjs.util.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/search")
public class PgSuggestController {

    private final PgSuggestService suggestService;

    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<List<String>>> getSuggestions(@RequestParam String keyword) {
        List<String> suggestions = suggestService.getSuggestions(keyword);
        return ResponseEntity.ok(ApiResponse.success(suggestions));
    }
}
