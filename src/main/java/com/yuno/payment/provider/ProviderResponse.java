package com.yuno.payment.provider;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponse {

    private boolean success;
    private String providerName;
    private String message;
    private String transactionId;
}