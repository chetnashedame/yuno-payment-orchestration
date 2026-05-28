package com.yuno.payment.routing;

import com.yuno.payment.model.PaymentMethod;
import com.yuno.payment.provider.PaymentProvider;
import com.yuno.payment.provider.ProviderAConnector;
import com.yuno.payment.provider.ProviderBConnector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoutingEngine {

    private final ProviderAConnector providerA;
    private final ProviderBConnector providerB;

    public PaymentProvider route(PaymentMethod paymentMethod) {
        log.info("Routing payment method: {}", paymentMethod);

        return switch (paymentMethod) {
            case CARD -> {
                log.info("Routing CARD → Provider A");
                yield providerA;
            }
            case UPI -> {
                log.info("Routing UPI → Provider B");
                yield providerB;
            }
            default -> throw new IllegalArgumentException(
                "Unsupported payment method: " + paymentMethod
            );
        };
    }

    public PaymentProvider getFailoverProvider(PaymentMethod paymentMethod) {
        log.warn("Activating failover for payment method: {}", paymentMethod);

        return switch (paymentMethod) {
            case CARD -> {
                log.warn("CARD failover → Provider B");
                yield providerB;
            }
            case UPI -> {
                log.warn("UPI failover → Provider A");
                yield providerA;
            }
            default -> throw new IllegalArgumentException(
                "Unsupported payment method: " + paymentMethod
            );
        };
    }
}