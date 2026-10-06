package com.karaoke.lounge.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 包厢预订单。
 *
 * <p>状态：已预订 / 已完成 / 已取消。人数不得超过包厢容量；时段须落在营业窗口内且不与同包厢其它预订重叠。</p>
 */
@Entity
@Table(name = "booking")
@Getter
@Setter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属包厢（与 Room.bookings 双向关联，由 room 维护外键 room_id） */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id", nullable = false)
    @JsonBackReference
    private Room room;

    /** 会员 id（仅存 id，不关联实体） */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /** 预订人数 */
    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    /** 已预订 / 已完成 / 已取消 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 前台现付 / 储值余额；仅已完成预订有值 */
    @Column(name = "settlement_method", length = 16)
    private String settlementMethod;

    @Column(name = "settlement_amount", precision = 10, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    /** 仅用于前后端传输房间 id（transient：JPA 不持久化，Jackson 仍序列化；持久化外键由 room 维护） */
    private transient Long roomId;

    public Long getRoomId() {
        if (roomId != null) {
            return roomId;
        }
        return room == null ? null : room.getId();
    }
}
