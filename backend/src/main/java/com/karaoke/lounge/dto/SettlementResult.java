package com.karaoke.lounge.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 结账结果：供前台核对消费单状态、扣款金额和扣款后的会员余额。 */
public record SettlementResult(
        Long bookingId,
        String bookingStatus,
        String paymentMethod,
        BigDecimal totalAmount,
        BigDecimal memberBalanceBefore,
        BigDecimal memberBalanceAfter,
        Long transactionId,
        LocalDateTime settledAt
) {
}
