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
 * 消费明细（按预订单记账）。
 *
 * <p>一笔预订可挂多笔消费；结账时合计需达到包厢最低消费，否则拒绝开单。</p>
 */
@Entity
@Table(name = "consumption")
@Getter
@Setter
public class Consumption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属预订单 id */
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    /** 消费项目名称，如 啤酒 / 果盘 / 欢唱套餐 */
    @Column(name = "item_name", nullable = false, length = 64)
    private String itemName;

    /** 金额（元） */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "at")
    private LocalDateTime at;
}
