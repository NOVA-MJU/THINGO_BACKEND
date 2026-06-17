package nova.mjs.domain.thingo.member.email;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nova.mjs.util.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/member/email")
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<String>> sendVerificationEmail(@Valid @RequestBody EmailVerificationRequestDto request) {
        String result = emailService.sendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/check")
    public ResponseEntity<ApiResponse<EmailVerificationResultDto>> checkEmailCode(@Valid @RequestBody EmailVerificationRequestDto request) {
        EmailVerificationResultDto result = emailService.verifyEmailCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
