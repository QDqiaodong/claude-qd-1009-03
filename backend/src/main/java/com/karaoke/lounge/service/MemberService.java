package com.karaoke.lounge.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.dto.MemberDetail;
import com.karaoke.lounge.dto.SettledBooking;
import com.karaoke.lounge.entity.Booking;
import com.karaoke.lounge.entity.Consumption;
import com.karaoke.lounge.entity.Member;
import com.karaoke.lounge.entity.MemberTransaction;
import com.karaoke.lounge.repository.BookingRepository;
import com.karaoke.lounge.repository.ConsumptionRepository;
import com.karaoke.lounge.repository.MemberRepository;
import com.karaoke.lounge.repository.MemberTransactionRepository;

/**
 * 会员业务。
 *
 * <p>规则：① 会员号唯一；② 手机号唯一；③ 等级须为合法枚举；④ 预存余额 &ge; 0。
 * 余额本身只保存当前值，所有初始储值、续充和消费扣款都必须追加一条资金流水。</p>
 */
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberTransactionRepository memberTransactionRepository;
    private final BookingRepository bookingRepository;
    private final ConsumptionRepository consumptionRepository;

    public MemberService(MemberRepository memberRepository,
                         MemberTransactionRepository memberTransactionRepository,
                         BookingRepository bookingRepository,
                         ConsumptionRepository consumptionRepository) {
        this.memberRepository = memberRepository;
        this.memberTransactionRepository = memberTransactionRepository;
        this.bookingRepository = bookingRepository;
        this.consumptionRepository = consumptionRepository;
    }

    @Transactional(readOnly = true)
    public List<Member> list(String level) {
        if (level != null && !level.isBlank()) {
            if (!Dict.isValidMemberLevel(level)) {
                throw new BizException("会员等级不合法：" + level);
            }
            return memberRepository.findByLevel(level);
        }
        return memberRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Member get(Long id) {
        return getMember(id);
    }

    @Transactional(readOnly = true)
    public MemberDetail detail(Long id) {
        Member member = getMember(id);
        List<Booking> bookings = bookingRepository
                .findByMemberIdAndStatusOrderBySettledAtDescIdDesc(id, "已完成");
        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        Map<Long, List<Consumption>> itemsByBooking = bookingIds.isEmpty()
                ? Collections.emptyMap()
                : consumptionRepository.findByBookingIdInOrderByAtDescIdDesc(bookingIds).stream()
                        .collect(Collectors.groupingBy(Consumption::getBookingId));

        List<SettledBooking> settledBookings = bookings.stream()
                .map(booking -> new SettledBooking(
                        booking.getId(),
                        booking.getRoom().getId(),
                        booking.getRoom().getRoomName(),
                        booking.getSettledAt(),
                        booking.getSettlementMethod(),
                        booking.getSettlementAmount(),
                        itemsByBooking.getOrDefault(booking.getId(), List.of())))
                .toList();
        return new MemberDetail(
                member,
                settledBookings,
                memberTransactionRepository.findByMemberIdOrderByCreatedAtDescIdDesc(id));
    }

    @Transactional
    public Member create(Member input) {
        if (input.getMemberNo() == null || input.getMemberNo().isBlank()) {
            throw new BizException("会员号不能为空");
        }
        if (memberRepository.existsByMemberNo(input.getMemberNo().trim())) {
            throw new BizException("会员号已存在：" + input.getMemberNo());
        }
        if (input.getMemberName() == null || input.getMemberName().isBlank()) {
            throw new BizException("会员姓名不能为空");
        }
        if (input.getPhone() == null || input.getPhone().isBlank()) {
            throw new BizException("手机号不能为空");
        }
        String phone = input.getPhone().trim();
        if (memberRepository.existsByPhone(phone)) {
            throw new BizException("手机号已注册：" + phone);
        }
        String level = (input.getLevel() == null || input.getLevel().isBlank()) ? "普通" : input.getLevel();
        if (!Dict.isValidMemberLevel(level)) {
            throw new BizException("会员等级不合法：" + level + "，应为 " + Dict.MEMBER_LEVELS);
        }
        BigDecimal balance = input.getBalance() == null ? BigDecimal.ZERO : input.getBalance();
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("预存余额不能为负数");
        }

        Member member = new Member();
        member.setMemberNo(input.getMemberNo().trim());
        member.setMemberName(input.getMemberName().trim());
        member.setPhone(phone);
        member.setLevel(level);
        member.setBalance(balance);
        Member saved = memberRepository.save(member);

        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            savedTransaction(saved, "初始储值", balance, balance, null,
                    "建档初始储值 " + balance + " 元");
        }
        return saved;
    }

    @Transactional
    public Member recharge(Long id, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("储值金额必须大于 0");
        }
        Member member = memberRepository.findForUpdateById(id)
                .orElseThrow(() -> new BizException("会员不存在：" + id));
        BigDecimal after = member.getBalance().add(amount);
        member.setBalance(after);
        Member saved = memberRepository.save(member);
        savedTransaction(saved, "储值", amount, after, null, "会员续充 " + amount + " 元");
        return saved;
    }

    @Transactional
    public Member updateLevel(Long id, String level) {
        Member member = getMember(id);
        if (!Dict.isValidMemberLevel(level)) {
            throw new BizException("会员等级不合法：" + level);
        }
        member.setLevel(level);
        return memberRepository.save(member);
    }

    private Member getMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new BizException("会员不存在：" + id));
    }

    private MemberTransaction savedTransaction(Member member, String type, BigDecimal amount,
                                               BigDecimal balanceAfter, Long bookingId, String remark) {
        MemberTransaction transaction = new MemberTransaction();
        transaction.setMemberId(member.getId());
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setBookingId(bookingId);
        transaction.setRemark(remark);
        transaction.setCreatedAt(LocalDateTime.now());
        return memberTransactionRepository.save(transaction);
    }
}
