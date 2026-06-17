package nova.mjs.domain.thingo.member.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class EmailVerificationRequestDto {

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "유효한 이메일 형식이 아닙니다.")
    private String email;

    private String code;
}
