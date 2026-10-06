package com.karaoke.lounge.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * 包厢清场任务（清场交接与验收放行闭环的核心单据）。
 *
 * <p>每场使用结束（「结束使用」）时生成，状态流转：
 * 待领取 → 清洁中 → 待验收 →（验收放行）已放行 /（退回整改）整改中 → 再提交回到待验收；
 * 带设备异常的任务在异常处置完成前不得放行，经理也可直接作停用结论（→ 已停用）。</p>
 *
 * <p>任务未闭环（待领取/清洁中/待验收/整改中）期间，包厢保持「待清场」，
 * 不可预订、不对外显示空闲 —— 可用性同时受清场状态约束。</p>
 *
 * <p>所有状态迁移均走条件 UPDATE（WHERE status = 期望前置状态），
 * 并发领取 / 重复验收只有第一个请求生效，其余得到可理解的中文提示。</p>
 */
@Entity
@Table(name = "cleaning_task")
@Getter
@Setter
public class CleaningTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 包厢 id（仅存 id，不关联实体，与 Consumption.bookingId 同一风格） */
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** 触发本次清场的场次（预订）id，可空（手工置使用中的包厢没有对应预订） */
    @Column(name = "booking_id")
    private Long bookingId;

    /** 待领取 / 清洁中 / 待验收 / 整改中 / 已放行 / 已停用 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 保洁负责人（领取人），领取后只有本人能提交 */
    @Column(name = "cleaner_name", length = 32)
    private String cleanerName;

    /** 验收经理（最近一次验收/退回/停用的经理） */
    @Column(name = "manager_name", length = 32)
    private String managerName;

    /** 最近一轮提交的卫生结果：合格 / 不合格 */
    @Column(name = "hygiene_result", length = 16)
    private String hygieneResult;

    /** 最近一轮提交的遗留物说明（空串视为无） */
    @Column(name = "leftover_items")
    private String leftoverItems;

    /** 最近一轮提交的设备异常说明（空串视为无异常） */
    @Column(name = "equipment_issue")
    private String equipmentIssue;

    /** 最近一轮是否上报了设备异常 */
    @Column(name = "has_equipment_issue", nullable = false)
    private Boolean hasEquipmentIssue = false;

    /** 设备异常是否已处置（无异常时无意义） */
    @Column(name = "issue_resolved", nullable = false)
    private Boolean issueResolved = false;

    /** 设备异常处置记录 */
    @Column(name = "issue_resolution")
    private String issueResolution;

    /** 最近一次退回整改要求 */
    @Column(name = "rework_note")
    private String reworkNote;

    /** 验收意见 / 停用结论 */
    @Column(name = "accept_note")
    private String acceptNote;

    /** 提交轮次（每次保洁提交 +1，整改积压可看往返次数） */
    @Column(name = "submit_round", nullable = false)
    private Integer round = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** 闭环时间（验收放行或停用结论） */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * 交接流水（不持久化，由 Service 批量装配后随任务一起返回前端，
     * 与 Booking.roomId 的 transient 用法一致）。
     */
    @Transient
    private List<CleaningRecord> records = new ArrayList<>();
}
