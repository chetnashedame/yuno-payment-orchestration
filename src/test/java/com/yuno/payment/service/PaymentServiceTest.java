package com.yuno.payment.service;

import com.yuno.payment.dto.PaymentRequest;
import com.yuno.payment.dto.PaymentResponse;
import com.yuno.payment.model.Payment;
import com.yuno.payment.model.PaymentMethod;
import com.yuno.payment.model.PaymentStatus;
import com.yuno.payment.provider.PaymentProvider;
import com.yuno.payment.provider.ProviderResponse;
import com.yuno.payment.repository.PaymentRepository;
import com.yuno.payment.routing.RoutingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RoutingEngine routingEngine;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private PaymentProvider paymentProvider;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequest cardRequest;
    private PaymentRequest upiRequest;
    private Payment savedPayment;

    @BeforeEach
    void setUp() {
        cardRequest = PaymentRequest.builder()
            .amount(new BigDecimal("1500.00"))
            .currency("INR")
            .paymentMethod(PaymentMethod.CARD)
            .merchantId("merchant-001")
            .description("Test payment")
            .build();

        upiRequest = PaymentRequest.builder()
            .amount(new BigDecimal("500.00"))
            .currency("INR")
            .paymentMethod(PaymentMethod.UPI)
            .merchantId("merchant-001")
            .build();

        savedPayment = Payment.builder()
            .id("pay-test-123")
            .amount(new BigDecimal("1500.00"))
            .currency("INR")
            .paymentMethod(PaymentMethod.CARD)
            .merchantId("merchant-001")
            .status(PaymentStatus.INITIATED)
            .provider("PENDING")
            .idempotencyKey("test-key-001")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    // ==================== POSITIVE TEST CASES ====================

    @Test
    @DisplayName("Should create CARD payment and route to Provider A")
    void shouldCreateCardPaymentSuccessfully() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(paymentRepository.findByIdempotencyKey(anyString()))
            .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(routingEngine.route(PaymentMethod.CARD)).thenReturn(paymentProvider);
        when(paymentProvider.processPayment(any(PaymentRequest.class)))
            .thenReturn(ProviderResponse.builder()
                .success(true)
                .providerName("PROVIDER_A")
                .message("Payment processed successfully")
                .transactionId("TXN_A_123")
                .build());

        // Act
        PaymentResponse response =
            paymentService.createPayment(cardRequest, "test-key-001");

        // Assert
        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        verify(routingEngine, times(1)).route(PaymentMethod.CARD);
        verify(paymentRepository, atLeastOnce()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should return cached response for duplicate idempotency key")
    void shouldReturnCachedResponseForDuplicateKey() {
        // Arrange
        PaymentResponse cachedResponse = PaymentResponse.builder()
            .paymentId("pay-test-123")
            .status(PaymentStatus.SUCCESS)
            .provider("PROVIDER_A")
            .amount(new BigDecimal("1500.00"))
            .currency("INR")
            .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(cachedResponse);

        // Act
        PaymentResponse response =
            paymentService.createPayment(cardRequest, "test-key-001");

        // Assert
        assertEquals("pay-test-123", response.getPaymentId());
        assertEquals(PaymentStatus.SUCCESS, response.getStatus());
        // Provider should NOT be called for duplicate
        verify(routingEngine, never()).route(any());
    }

    @Test
    @DisplayName("Should fetch existing payment by ID")
    void shouldFetchPaymentById() {
        // Arrange
        when(paymentRepository.findById("pay-test-123"))
            .thenReturn(Optional.of(savedPayment));

        // Act
        PaymentResponse response = paymentService.getPayment("pay-test-123");

        // Assert
        assertNotNull(response);
        assertEquals("pay-test-123", response.getPaymentId());
        assertEquals(PaymentStatus.INITIATED, response.getStatus());
    }

    @Test
    @DisplayName("Should return DB payment when Redis is unavailable")
    void shouldFallbackToDBWhenRedisUnavailable() {
        // Arrange
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));
        when(paymentRepository.findByIdempotencyKey("test-key-001"))
            .thenReturn(Optional.of(savedPayment));

        // Act
        PaymentResponse response =
            paymentService.createPayment(cardRequest, "test-key-001");

        // Assert
        assertNotNull(response);
        assertEquals("pay-test-123", response.getPaymentId());
    }

    // ==================== NEGATIVE TEST CASES ====================

    @Test
    @DisplayName("Should throw PaymentNotFoundException for invalid ID")
    void shouldThrowExceptionForInvalidPaymentId() {
        // Arrange
        when(paymentRepository.findById("invalid-id"))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
            com.yuno.payment.exception.PaymentNotFoundException.class,
            () -> paymentService.getPayment("invalid-id")
        );
    }

    @Test
    @DisplayName("Should handle provider failure and attempt failover")
    void shouldAttemptFailoverWhenPrimaryProviderFails() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(paymentRepository.findByIdempotencyKey(anyString()))
            .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(routingEngine.route(PaymentMethod.CARD)).thenReturn(paymentProvider);
        when(paymentProvider.processPayment(any()))
            .thenReturn(ProviderResponse.builder()
                .success(false)
                .providerName("PROVIDER_A")
                .message("Provider A declined")
                .build());

        PaymentProvider failoverProvider = mock(PaymentProvider.class);
        when(routingEngine.getFailoverProvider(PaymentMethod.CARD))
            .thenReturn(failoverProvider);
        when(failoverProvider.processPayment(any()))
            .thenReturn(ProviderResponse.builder()
                .success(true)
                .providerName("PROVIDER_B")
                .message("Failover success")
                .build());

        // Act
        PaymentResponse response =
            paymentService.createPayment(cardRequest, "test-key-004");

        // Assert
        assertNotNull(response);
        verify(routingEngine, times(1)).getFailoverProvider(PaymentMethod.CARD);
    }
}