package com.karaoke.lounge.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 会员档案。
 *
 * <p>等级：普通 / 银卡 / 金卡 / 钻石；预存余额用于结账抵扣。</p>
 */
@Entity
@Table(name = "member")
@Getter
@Setter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 会员号，唯一 */
    @Column(name = "member_no", nullable = false, unique = true, length = 16)
    private String memberNo;

    @Column(name = "member_name", nullable = false, length = 32)
    private String memberName;

    @Column(name = "phone", nullable = false, length = 24)
    private String phone;

    /** 普通 / 银卡 / 金卡 / 钻石 */
    @Column(name = "level", nullable = false, length = 16)
    private String level;

    /** 预存余额（元） */
    @Column(name = "balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal balance;
}
