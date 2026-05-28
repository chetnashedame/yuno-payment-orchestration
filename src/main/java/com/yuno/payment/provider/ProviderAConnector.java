package com.yuno.payment.provider;

import com.yuno.payment.dto.PaymentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Random;

@Slf4j
@Component
public class ProviderAConnector implements PaymentProvider {

    private static final String PROVIDER_NAME = "PROVIDER_A";
    private final Random random = new Random();

    @Override
    public ProviderResponse processPayment(PaymentRequest request) {
        log.info("Provider A processing CARD payment of {} {}",
            request.getAmount(), request.getCurrency());

        // Simulate real provider call with 80% success rate
        simulateNetworkDelay();
        boolean success = random.nextInt(10) < 8;

        if (success) {
            log.info("Provider A: payment SUCCESS");
            return ProviderResponse.builder()
                .success(true)
                .providerName(PROVIDER_NAME)
                .message("Payment processed successfully")
                .transactionId("TXN_A_" + System.currentTimeMillis())
                .build();
        } else {
            log.warn("Provider A: payment FAILED");
            return ProviderResponse.builder()
                .success(false)
                .providerName(PROVIDER_NAME)
                .message("Provider A declined the transaction")
                .build();
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private void simulateNetworkDelay() {
        try {
            // Simulate 100-300ms network latency
            Thread.sleep(100 + random.nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}