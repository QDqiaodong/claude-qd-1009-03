package com.karaoke.lounge.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会员储值资金流水（只追加）。
 *
 * <p>储值为正、消费扣款为负；{@code balanceAfter} 保存该笔流水落账后的余额。
 * 扣款流水必须关联预订，且同一预订最多只有一条扣款流水。</p>
 */
@Entity
@Table(name = "member_transaction")
@Getter
@Setter
public class MemberTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 储值 / 消费扣款 / 初始储值 */
    @Column(name = "type", nullable = false, length = 16)
    private String type;

    /** 正数为入账，负数为扣款 */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    /** 扣款时关联的预订；储值流水为空 */
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
