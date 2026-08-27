package com.cmcu.itstudy.scheduler;

import com.cmcu.itstudy.service.contract.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background scheduler to automatically cancel payments that remain in PENDING status for too long.
 * Configured by default to cancel payments pending for more than 15 minutes.
 */
@Slf4j
@Component
@Lazy(false)
public class PaymentExpirationScheduler {

    private final PaymentService paymentService;

    @Value("${app.payment.expiration-minutes:15}")
    private int expirationMinutes;

    public PaymentExpirationScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Periodically checks and cancels expired pending payments.
     * Runs every 1 minute (60,000ms) with a 10-second initial delay after startup.
     */
    @Scheduled(fixedDelayString = "${app.payment.expiration-check-ms:60000}", initialDelay = 10000)
    public void executeExpiredPaymentCancellation() {
        try {
            int cancelled = paymentService.cancelExpiredPendingPayments(expirationMinutes);
            if (cancelled > 0) {
                log.info("Scheduled task cancelled {} expired pending payment(s) older than {} minutes",
                        cancelled, expirationMinutes);
            }
        } catch (Exception ex) {
            log.error("Failed to execute expired payment cancellation: {}", ex.getMessage(), ex);
        }
    }
}
