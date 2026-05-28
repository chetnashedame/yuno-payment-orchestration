package com.yuno.payment.provider;

import com.yuno.payment.dto.PaymentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Random;

@Slf4j
@Component
public class ProviderBConnector implements PaymentProvider {

    private static final String PROVIDER_NAME = "PROVIDER_B";
    private final Random random = new Random();

    @Override
    public ProviderResponse processPayment(PaymentRequest request) {
        log.info("Provider B processing UPI payment of {} {}",
            request.getAmount(), request.getCurrency());

        simulateNetworkDelay();
        boolean success = random.nextInt(10) < 8;

        if (success) {
            log.info("Provider B: payment SUCCESS");
            return ProviderResponse.builder()
                .success(true)
                .providerName(PROVIDER_NAME)
                .message("UPI payment processed successfully")
                .transactionId("TXN_B_" + System.currentTimeMillis())
                .build();
        } else {
            log.warn("Provider B: payment FAILED");
            return ProviderResponse.builder()
                .success(false)
                .providerName(PROVIDER_NAME)
                .message("Provider B declined the transaction")
                .build();
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private void simulateNetworkDelay() {
        try {
            Thread.sleep(100 + random.nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}