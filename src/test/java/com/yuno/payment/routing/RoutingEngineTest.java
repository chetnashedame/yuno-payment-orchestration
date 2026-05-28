package com.yuno.payment.routing;

import com.yuno.payment.model.PaymentMethod;
import com.yuno.payment.provider.PaymentProvider;
import com.yuno.payment.provider.ProviderAConnector;
import com.yuno.payment.provider.ProviderBConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RoutingEngineTest {

    @Mock
    private ProviderAConnector providerA;

    @Mock
    private ProviderBConnector providerB;

    @InjectMocks
    private RoutingEngine routingEngine;

    // ==================== POSITIVE TEST CASES ====================

    @Test
    @DisplayName("Should route CARD payment to Provider A")
    void shouldRouteCardToProviderA() {
        PaymentProvider provider = routingEngine.route(PaymentMethod.CARD);
        assertEquals(providerA, provider);
    }

    @Test
    @DisplayName("Should route UPI payment to Provider B")
    void shouldRouteUpiToProviderB() {
        PaymentProvider provider = routingEngine.route(PaymentMethod.UPI);
        assertEquals(providerB, provider);
    }

    @Test
    @DisplayName("Should failover CARD to Provider B")
    void shouldFailoverCardToProviderB() {
        PaymentProvider failover =
            routingEngine.getFailoverProvider(PaymentMethod.CARD);
        assertEquals(providerB, failover);
    }

    @Test
    @DisplayName("Should failover UPI to Provider A")
    void shouldFailoverUpiToProviderA() {
        PaymentProvider failover =
            routingEngine.getFailoverProvider(PaymentMethod.UPI);
        assertEquals(providerA, failover);
    }
}