package com.karaoke.lounge.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.dto.SettlementResult;
import com.karaoke.lounge.entity.Booking;
import com.karaoke.lounge.entity.Consumption;
import com.karaoke.lounge.entity.Member;
import com.karaoke.lounge.entity.MemberTransaction;
import com.karaoke.lounge.repository.BookingRepository;
import com.karaoke.lounge.repository.ConsumptionRepository;
import com.karaoke.lounge.repository.MemberRepository;
import com.karaoke.lounge.repository.MemberTransactionRepository;
import com.karaoke.lounge.repository.RoomRepository;

/**
 * 消费业务。
 *
 * <p>规则：① 消费必须挂在有效、未结账预订上；② 金额须 &gt; 0；
 * ③ 结账开单时，该预订合计消费须达到包厢最低消费，否则拒绝开单。</p>
 *
 * <p>结账一致性取舍：不拆分「先扣余额、再改消费单、最后补流水」的多个步骤，
 * 而是在一个短事务内按固定顺序加锁（储值结算先锁会员再锁消费单），校验通过后一次性提交
 * 余额扣减、资金流水和消费单状态。结账失败或进程中断时由数据库事务整体回滚，
 * 宁可本次结账失败重试，也不留下单边账。</p>
 */
@Service
public class ConsumptionService {

    private static final String PAY_ON_SITE = "前台现付";
    private static final String PAY_BALANCE = "储值余额";

    private final ConsumptionRepository consumptionRepository;
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;
    private final MemberTransactionRepository memberTransactionRepository;

    public ConsumptionService(ConsumptionRepository consumptionRepository,
                              BookingRepository bookingRepository,
                              RoomRepository roomRepository,
                              MemberRepository memberRepository,
                              MemberTransactionRepository memberTransactionRepository) {
        this.consumptionRepository = consumptionRepository;
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.memberTransactionRepository = memberTransactionRepository;
    }

    @Transactional(readOnly = true)
    public List<Consumption> listByBooking(Long bookingId) {
        if (bookingId == null) {
            return consumptionRepository.findAll();
        }
        return consumptionRepository.findByBookingId(bookingId);
    }

    @Transactional
    public Consumption create(Consumption input) {
        if (input.getBookingId() == null) {
            throw new BizException("消费必须挂在预订上");
        }
        // 锁定消费单：挂单与结账互斥，避免结账合计计算后又有新明细写入已完成单。
        Booking booking = bookingRepository.findForUpdateById(input.getBookingId())
                .orElseThrow(() -> new BizException("预订不存在：" + input.getBookingId()));
        if ("已取消".equals(booking.getStatus())) {
            throw new BizException("已取消的预订不能挂消费");
        }
        if ("已完成".equals(booking.getStatus())) {
            throw new BizException("已结账的预订不能继续挂消费");
        }
        if (input.getItemName() == null || input.getItemName().isBlank()) {
            throw new BizException("消费项目名称不能为空");
        }
        if (input.getAmount() == null || input.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("消费金额必须大于 0");
        }
        Consumption c = new Consumption();
        c.setBookingId(input.getBookingId());
        c.setItemName(input.getItemName().trim());
        c.setAmount(input.getAmount());
        c.setAt(LocalDateTime.now());
        return consumptionRepository.save(c);
    }

    /**
     * 结账开单：校验最低消费，并按所选结算方式原子完成。
     *
     * <p>只有明确选择「储值余额」时才读取、锁定并扣减会员余额；选择「前台现付」时
     * 不做余额校验，也不写会员资金流水。</p>
     */
    @Transactional
    public SettlementResult settle(Long bookingId, String paymentMethodInput) {
        String paymentMethod = normalizePaymentMethod(paymentMethodInput);
        Member member = null;

        // 储值结算先锁会员、再锁消费单，保证同一会员的并发扣款都按相同顺序拿锁，避免死锁；
        // 前台现付不触碰会员余额，只锁消费单。
        if (PAY_BALANCE.equals(paymentMethod)) {
            Booking readable = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new BizException("预订不存在：" + bookingId));
            member = memberRepository.findForUpdateById(readable.getMemberId())
                    .orElseThrow(() -> new BizException("会员不存在：" + readable.getMemberId()));
        }

        Booking booking = bookingRepository.findForUpdateById(bookingId)
                .orElseThrow(() -> new BizException("预订不存在：" + bookingId));
        if ("已完成".equals(booking.getStatus())) {
            return buildSettledResult(booking, paymentMethod);
        }
        if ("已取消".equals(booking.getStatus())) {
            throw new BizException("已取消的预订无法结账");
        }

        BigDecimal total = consumptionRepository.sumAmountByBookingId(bookingId);
        BigDecimal minSpend = booking.getRoom().getMinSpend();
        if (total.compareTo(minSpend) < 0) {
            throw new BizException("包厢 " + booking.getRoom().getRoomName()
                    + " 最低消费 " + minSpend + " 元，当前消费合计 " + total + " 元，未达标无法开单结账");
        }

        BigDecimal balanceBefore = null;
        BigDecimal balanceAfter = null;
        Long transactionId = null;

        if (PAY_BALANCE.equals(paymentMethod) && !member.getId().equals(booking.getMemberId())) {
            throw new BizException("消费单会员信息已变化，请刷新后重试结账");
        }

        if (PAY_BALANCE.equals(paymentMethod)) {
            balanceBefore = member.getBalance();
            if (balanceBefore.compareTo(total) < 0) {
                // 关键异常分支：在任何余额更新/流水写入前拒绝，消费单也保持未结账。
                throw new BizException("储值余额不足：当前余额 " + balanceBefore
                        + " 元，本次需扣 " + total + " 元，请选择其他结算方式或先续充");
            }

            balanceAfter = balanceBefore.subtract(total);
            member.setBalance(balanceAfter);
            memberRepository.save(member);

            MemberTransaction transaction = new MemberTransaction();
            transaction.setMemberId(member.getId());
            transaction.setType("消费扣款");
            transaction.setAmount(total.negate());
            transaction.setBalanceAfter(balanceAfter);
            transaction.setBookingId(booking.getId());
            transaction.setRemark("预订#" + booking.getId() + " 储值余额结账");
            transaction.setCreatedAt(LocalDateTime.now());
            transactionId = memberTransactionRepository.save(transaction).getId();
        }

        LocalDateTime settledAt = LocalDateTime.now();
        booking.setStatus("已完成");
        booking.setSettlementMethod(paymentMethod);
        booking.setSettlementAmount(total);
        booking.setSettledAt(settledAt);
        bookingRepository.save(booking);
        booking.getRoom().setStatus("使用中");
        roomRepository.save(booking.getRoom());

        return new SettlementResult(booking.getId(), booking.getStatus(), paymentMethod, total,
                balanceBefore, balanceAfter, transactionId, settledAt);
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return PAY_ON_SITE;
        }
        String method = paymentMethod.trim();
        if (!PAY_ON_SITE.equals(method) && !PAY_BALANCE.equals(method)) {
            throw new BizException("结算方式不合法：" + paymentMethod + "，应为 " + PAY_ON_SITE + " / " + PAY_BALANCE);
        }
        return method;
    }

    private SettlementResult buildSettledResult(Booking booking, String requestedMethod) {
        String existingMethod = booking.getSettlementMethod() == null
                ? PAY_ON_SITE : booking.getSettlementMethod();
        if (!existingMethod.equals(requestedMethod)) {
            throw new BizException("该消费单已按「" + existingMethod + "」结账，不能改用「"
                    + requestedMethod + "」重复结算");
        }

        MemberTransaction transaction = memberTransactionRepository.findByBookingId(booking.getId());
        BigDecimal balanceBefore = null;
        BigDecimal balanceAfter = null;
        if (transaction != null) {
            balanceAfter = transaction.getBalanceAfter();
            balanceBefore = balanceAfter.add(transaction.getAmount().abs());
        }
        return new SettlementResult(booking.getId(), booking.getStatus(), existingMethod,
                booking.getSettlementAmount(), balanceBefore, balanceAfter,
                transaction == null ? null : transaction.getId(), booking.getSettledAt());
    }
}
