package com.karaoke.lounge.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * 包厢台账。
 *
 * <p>状态流转：空闲 → 预订 → 使用中 →【结束使用】→ 待清场 →【验收放行】→ 空闲；停用为旁路。
 * 容量用于预订人数上限校验，最低消费用于结账校验。
 * 可用性取舍：待清场（清场未验收）与停用期间一律不可预订、不对外显示空闲。</p>
 *
 * <p>演示点：对预订集合使用 {@code @OneToMany(mappedBy="room")} + {@link FetchMode#SUBSELECT}，
 * 在业务方法里一次性触发子查询，把【所有包厢】的预订一并拉取，避免逐包厢查询的 N+1。</p>
 */
@Entity
@Table(name = "room")
@Getter
@Setter
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 包厢编号，唯一，如 K01 */
    @Column(name = "room_code", nullable = false, unique = true, length = 16)
    private String roomCode;

    /** 包厢名称，如 小包 / 中包 / VIP大包 */
    @Column(name = "room_name", nullable = false, length = 64)
    private String roomName;

    /** 可容纳人数 */
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    /** 最低消费（元） */
    @Column(name = "min_spend", nullable = false, precision = 10, scale = 2)
    private BigDecimal minSpend;

    /** 空闲 / 预订 / 使用中 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 该包厢的所有预订（SUBSELECT 一次性拉取，避免 N+1）。 */
    @OneToMany(mappedBy = "room", fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @JsonManagedReference
    private List<Booking> bookings = new ArrayList<>();
}
