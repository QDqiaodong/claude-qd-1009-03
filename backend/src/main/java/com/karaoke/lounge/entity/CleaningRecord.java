package com.karaoke.lounge.entity;

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
 * 清场交接流水（只追加，不修改不删除）。
 *
 * <p>任务的每一次创建 / 领取 / 提交 / 验收放行 / 退回整改 / 异常处置 / 停用都落一条记录，
 * 提交与退回时把当轮的卫生结果、遗留物、设备异常、整改要求完整快照进 detail ——
 * 退回整改后前一次的问题与处理记录因此永久保留，可从包厢详情一路追溯。</p>
 */
@Entity
@Table(name = "cleaning_record")
@Getter
@Setter
public class CleaningRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属清场任务 id（仅存 id，不关联实体） */
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** 创建 / 领取 / 提交 / 验收放行 / 退回整改 / 异常处置 / 停用 */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    /** 操作人姓名（保洁 / 经理 / 前台） */
    @Column(name = "operator_name", nullable = false, length = 32)
    private String operatorName;

    /** 当轮内容快照：卫生结果、遗留物、设备异常、整改要求、验收意见等 */
    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
