package com.karaoke.lounge.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.karaoke.lounge.entity.Consumption;

/** 会员详情中的一场已结账消费。 */
public record SettledBooking(
        Long bookingId,
        Long roomId,
        String roomName,
        LocalDateTime settledAt,
        String settlementMethod,
        BigDecimal settlementAmount,
        List<Consumption> items
) {
}
