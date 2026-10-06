package com.karaoke.lounge.dto;

import java.util.List;

import com.karaoke.lounge.entity.Member;
import com.karaoke.lounge.entity.MemberTransaction;

/** 管理员查看会员详情：余额、已结账消费和每笔资金流水分开呈现。 */
public record MemberDetail(
        Member member,
        List<SettledBooking> settledBookings,
        List<MemberTransaction> transactions
) {
}
