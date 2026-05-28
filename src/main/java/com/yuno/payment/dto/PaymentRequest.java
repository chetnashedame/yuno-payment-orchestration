package com.yuno.payment.dto;

import com.yuno.payment.model.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    @Digits(integer = 10, fraction = 2,
        message = "Max 10 integer digits, 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3,
        message = "Currency must be a 3-letter ISO code eg. INR")
    private String currency;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotBlank(message = "Merchant ID is required")
    private String merchantId;

    @Size(max = 255, message = "Description too long")
    private String description;
}