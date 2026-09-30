package org.example.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminTrackingRequest {

    @NotBlank(message = "택배사는 필수입니다")
    @Size(max = 30, message = "택배사는 30자 이하여야 합니다")
    private String courier;

    @NotBlank(message = "운송장 번호는 필수입니다")
    @Size(max = 50, message = "운송장 번호는 50자 이하여야 합니다")
    private String trackingNumber;
}
