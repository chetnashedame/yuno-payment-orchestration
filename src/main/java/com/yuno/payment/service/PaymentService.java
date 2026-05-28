package com.yuno.payment.service;

import com.yuno.payment.dto.PaymentRequest;
import com.yuno.payment.dto.PaymentResponse;
import com.yuno.payment.exception.PaymentNotFoundException;
import com.yuno.payment.model.Payment;
import com.yuno.payment.model.PaymentStatus;
import com.yuno.payment.provider.PaymentProvider;
import com.yuno.payment.provider.ProviderResponse;
import com.yuno.payment.repository.PaymentRepository;
import com.yuno.payment.routing.RoutingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RoutingEngine routingEngine;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    @Transactional
    public PaymentResponse createPayment(
            PaymentRequest request, String idempotencyKey) {

        // Step 1 — Check idempotency (Redis first)
        PaymentResponse cachedResponse = checkIdempotencyCache(idempotencyKey);
        if (cachedResponse != null) {
            log.info("Duplicate request detected for key: {}", idempotencyKey);
            return cachedResponse;
        }

        // Step 2 — Check idempotency (DB fallback)
        Optional<Payment> existingPayment =
            paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            log.info("Payment found in DB for idempotency key: {}", idempotencyKey);
            return mapToResponse(existingPayment.get(), "Duplicate request");
        }

        // Step 3 — Save initial payment record (INITIATED)
        Payment payment = Payment.builder()
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .paymentMethod(request.getPaymentMethod())
            .merchantId(request.getMerchantId())
            .description(request.getDescription())
            .idempotencyKey(idempotencyKey)
            .status(PaymentStatus.INITIATED)
            .provider("PENDING")
            .build();

        payment = paymentRepository.save(payment);
        log.info("Payment created with id: {}", payment.getId());

        // Step 4 — Route to correct provider
        PaymentProvider provider = routingEngine.route(request.getPaymentMethod());

        // Step 5 — Process with retry
        payment.setStatus(PaymentStatus.PROCESSING);
        payment = paymentRepository.save(payment);

        PaymentResponse response = processWithRetryAndFailover(
            payment, request, provider);

        // Step 6 — Cache the result in Redis
        cacheIdempotencyResponse(idempotencyKey, response);

        return response;
    }

    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public ProviderResponse callProvider(
            PaymentProvider provider, PaymentRequest request) {
        ProviderResponse response = provider.processPayment(request);
        if (!response.isSuccess()) {
            throw new RuntimeException(
                "Provider failed: " + response.getMessage());
        }
        return response;
    }

    private PaymentResponse processWithRetryAndFailover(
            Payment payment, PaymentRequest request, PaymentProvider provider) {

        try {
            // Try primary provider with retries
            ProviderResponse providerResponse = callProvider(provider, request);
            return updateAndRespond(payment, providerResponse, PaymentStatus.SUCCESS);

        } catch (Exception e) {
            log.warn("Primary provider failed after retries. Attempting failover...");

            try {
                // Try failover provider
                PaymentProvider failoverProvider =
                    routingEngine.getFailoverProvider(request.getPaymentMethod());
                ProviderResponse failoverResponse =
                    failoverProvider.processPayment(request);

                if (failoverResponse.isSuccess()) {
                    log.info("Failover provider succeeded");
                    return updateAndRespond(
                        payment, failoverResponse, PaymentStatus.SUCCESS);
                }
            } catch (Exception failoverEx) {
                log.error("Failover also failed: {}", failoverEx.getMessage());
            }

            // Both providers failed
            payment.setStatus(PaymentStatus.FAILED);
            payment.setProvider("NONE");
            paymentRepository.save(payment);

            return PaymentResponse.builder()
                .paymentId(payment.getId())
                .status(PaymentStatus.FAILED)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .message("Payment failed after all retries and failover")
                .createdAt(payment.getCreatedAt())
                .build();
        }
    }

    public PaymentResponse getPayment(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return mapToResponse(payment, null);
    }

    private PaymentResponse updateAndRespond(
            Payment payment, ProviderResponse providerResponse,
            PaymentStatus status) {
        payment.setStatus(status);
        payment.setProvider(providerResponse.getProviderName());
        paymentRepository.save(payment);

        return PaymentResponse.builder()
            .paymentId(payment.getId())
            .status(status)
            .provider(providerResponse.getProviderName())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .merchantId(payment.getMerchantId())
            .message(providerResponse.getMessage())
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            .build();
    }

    private PaymentResponse mapToResponse(Payment payment, String message) {
        return PaymentResponse.builder()
            .paymentId(payment.getId())
            .status(payment.getStatus())
            .provider(payment.getProvider())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .merchantId(payment.getMerchantId())
            .message(message)
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            .build();
    }

    @SuppressWarnings("unchecked")
    private PaymentResponse checkIdempotencyCache(String idempotencyKey) {
        try {
            Object cached = redisTemplate.opsForValue()
                .get(IDEMPOTENCY_PREFIX + idempotencyKey);
            return cached != null ? (PaymentResponse) cached : null;
        } catch (Exception e) {
            log.warn("Redis unavailable, falling back to DB check");
            return null;
        }
    }

    private void cacheIdempotencyResponse(
            String idempotencyKey, PaymentResponse response) {
        try {
            redisTemplate.opsForValue().set(
                IDEMPOTENCY_PREFIX + idempotencyKey,
                response,
                IDEMPOTENCY_TTL_HOURS,
                TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.warn("Could not cache in Redis: {}", e.getMessage());
        }
    }
}