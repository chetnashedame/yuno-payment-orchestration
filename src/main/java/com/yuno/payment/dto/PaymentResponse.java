package com.yuno.payment.dto;

import com.yuno.payment.model.PaymentStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String paymentId;
    private PaymentStatus status;
    private String provider;
    private BigDecimal amount;
    private String currency;
    private String merchantId;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}