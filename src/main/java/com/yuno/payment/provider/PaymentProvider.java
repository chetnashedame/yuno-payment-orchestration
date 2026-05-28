package com.yuno.payment.provider;

import com.yuno.payment.dto.PaymentRequest;

public interface PaymentProvider {
    
    ProviderResponse processPayment(PaymentRequest request);
    
    String getProviderName();
}