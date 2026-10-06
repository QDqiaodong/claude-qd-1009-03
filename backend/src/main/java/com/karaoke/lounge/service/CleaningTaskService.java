package com.karaoke.lounge.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.entity.Booking;
import com.karaoke.lounge.entity.CleaningRecord;
import com.karaoke.lounge.entity.CleaningTask;
import com.karaoke.lounge.entity.Room;
import com.karaoke.lounge.repository.BookingRepository;
import com.karaoke.lounge.repository.CleaningRecordRepository;
import com.karaoke.lounge.repository.CleaningTaskRepository;
import com.karaoke.lounge.repository.RoomRepository;

/**
 * 清场交接与验收放行闭环。
 *
 * <p>闭环链路：场次结束（{@link #finishSession}）→ 待领取 → 保洁领取 → 清洁中
 * → 保洁提交（卫生结果 / 遗留物 / 设备异常）→ 待验收 → 经理验收放行（包厢回空闲）
 * 或退回整改（整改中，保洁按整改要求返工后再次提交）。</p>
 *
 * <p>硬约束：
 * ① 任务未闭环期间包厢保持「待清场」，不可预订、不对外显示空闲（见 BookingService / RoomService）；
 * ② 带设备异常的任务即使卫生合格也不能放行，须先「异常处置」登记处理结果，或由经理作「停用结论」；
 * ③ 所有迁移走条件 UPDATE，并发领取 / 重复提交 / 重复验收只有第一个生效，
 *    其余在重读当前状态后得到可理解的中文提示；
 * ④ 每次操作追加一条 {@link CleaningRecord}，退回整改时把前一轮问题快照进流水，永久可查。</p>
 */
@Service
public class CleaningTaskService {

    private final CleaningTaskRepository cleaningTaskRepository;
    private final CleaningRecordRepository cleaningRecordRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public CleaningTaskService(CleaningTaskRepository cleaningTaskRepository,
                               CleaningRecordRepository cleaningRecordRepository,
                               RoomRepository roomRepository,
                               BookingRepository bookingRepository) {
        this.cleaningTaskRepository = cleaningTaskRepository;
        this.cleaningRecordRepository = cleaningRecordRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    // ---------------- 查询 ----------------

    /**
     * 任务列表。status 支持逗号分隔多值（前端三个栏目：保洁待办 / 经理待验收 / 整改积压）；
     * roomId 用于包厢详情追溯整条交接记录。每条任务都装配好交接流水（一次批量查询，避免 N+1）。
     */
    @Transactional(readOnly = true)
    public List<CleaningTask> list(String status, Long roomId) {
        List<String> statuses = parseStatuses(status);
        List<CleaningTask> tasks;
        if (roomId != null && statuses != null) {
            tasks = cleaningTaskRepository.findByRoomIdAndStatusInOrderByIdDesc(roomId, statuses);
        } else if (statuses != null) {
            tasks = cleaningTaskRepository.findByStatusInOrderByIdDesc(statuses);
        } else if (roomId != null) {
            tasks = cleaningTaskRepository.findByRoomIdOrderByIdDesc(roomId);
        } else {
            tasks = cleaningTaskRepository.findAllByOrderByIdDesc();
        }
        attachRecords(tasks);
        return tasks;
    }

    @Transactional(readOnly = true)
    public CleaningTask get(Long id) {
        CleaningTask task = cleaningTaskRepository.findById(id)
                .orElseThrow(() -> new BizException("清场任务不存在：" + id));
        attachRecords(List.of(task));
        return task;
    }

    private List<String> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        List<String> statuses = Arrays.stream(status.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        for (String s : statuses) {
            if (!Dict.isValidCleaningStatus(s)) {
                throw new BizException("清场任务状态不合法：" + s + "，应为 " + Dict.CLEANING_STATUSES);
            }
        }
        return statuses.isEmpty() ? null : statuses;
    }

    private void attachRecords(List<CleaningTask> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        List<Long> ids = tasks.stream().map(CleaningTask::getId).toList();
        Map<Long, List<CleaningRecord>> byTask = new LinkedHashMap<>();
        for (CleaningRecord r : cleaningRecordRepository.findByTaskIdInOrderByCreatedAtAscIdAsc(ids)) {
            byTask.computeIfAbsent(r.getTaskId(), k -> new ArrayList<>()).add(r);
        }
        for (CleaningTask t : tasks) {
            t.setRecords(byTask.getOrDefault(t.getId(), new ArrayList<>()));
        }
    }

    // ---------------- 场次结束：生成待清场任务 ----------------

    /**
     * 结束使用：包厢 使用中 → 待清场，并生成待领取的清场任务。
     * 从此包厢不可直接回到可接待状态，必须走完「保洁提交 → 经理验收放行」。
     * 包厢行加悲观写锁，防止同包厢并发生成重复任务。
     */
    @Transactional
    public CleaningTask finishSession(Long roomId, String operator) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BizException("包厢不存在：" + roomId));
        if (!"使用中".equals(room.getStatus())) {
            throw new BizException("包厢 " + room.getRoomName() + " 当前为「" + room.getStatus()
                    + "」，只有使用中的包厢才能结束使用并生成清场任务");
        }
        if (cleaningTaskRepository.existsByRoomIdAndStatusIn(roomId, Dict.CLEANING_OPEN_STATUSES)) {
            throw new BizException("包厢 " + room.getRoomName() + " 已有未闭环的清场任务，请勿重复生成");
        }
        room.setStatus("待清场");
        roomRepository.save(room);

        CleaningTask task = new CleaningTask();
        task.setRoomId(room.getId());
        Booking latest = bookingRepository.findTopByRoom_IdAndStatusNotOrderByEndAtDesc(room.getId(), "已取消");
        task.setBookingId(latest == null ? null : latest.getId());
        task.setStatus("待领取");
        task.setHasEquipmentIssue(false);
        task.setIssueResolved(false);
        task.setRound(0);
        task.setCreatedAt(LocalDateTime.now());
        CleaningTask saved = cleaningTaskRepository.save(task);

        addRecord(saved.getId(), "创建", blankToDefault(operator, "前台"),
                "包厢 " + room.getRoomName() + " 场次结束，生成待清场任务，验收放行前不可接待");
        return get(saved.getId());
    }

    // ---------------- 保洁：领取 / 提交 ----------------

    /** 领取任务：仅「待领取」可领；两人同时领取只有一人命中，另一人得到明确提示 */
    @Transactional
    public CleaningTask claim(Long taskId, String operator) {
        String cleaner = requireName(operator, "领取人姓名不能为空");
        CleaningTask cur = lock(taskId);
        int updated = cleaningTaskRepository.claimIfPending(taskId, cleaner, LocalDateTime.now());
        if (updated == 0) {
            // cur 是 FOR UPDATE 当前读到的最新状态（锁在本事务手里），提示必然准确
            if (cur.getCleanerName() != null && !"待领取".equals(cur.getStatus())) {
                throw new BizException("手慢了，该任务已由「" + cur.getCleanerName()
                        + "」领取，当前状态「" + cur.getStatus() + "」");
            }
            throw new BizException("任务当前状态为「" + cur.getStatus() + "」，不能领取");
        }
        addRecord(taskId, "领取", cleaner, "保洁领取任务，开始清场");
        return get(taskId);
    }

    /**
     * 提交清场结果：仅任务负责人在「清洁中 / 整改中」可提交。
     * 卫生结果、遗留物、设备异常整体快照进流水；上报设备异常则本轮须先处置才能放行。
     */
    @Transactional
    public CleaningTask submit(Long taskId, CleaningTask input) {
        String cleaner = requireName(input.getCleanerName(), "提交人（保洁）姓名不能为空");
        if (!Dict.isValidHygieneResult(input.getHygieneResult())) {
            throw new BizException("卫生结果不合法，应为 " + Dict.HYGIENE_RESULTS);
        }
        String leftover = trimToNull(input.getLeftoverItems());
        String issue = trimToNull(input.getEquipmentIssue());
        boolean hasIssue = issue != null;
        CleaningTask cur = lock(taskId);
        int updated = cleaningTaskRepository.submitIfOwner(taskId, cleaner,
                input.getHygieneResult(), leftover, issue, hasIssue, LocalDateTime.now());
        if (updated == 0) {
            if (cur.getCleanerName() != null && !cur.getCleanerName().equals(cleaner)) {
                throw new BizException("该任务负责人是「" + cur.getCleanerName() + "」，只有负责人本人能提交清场结果");
            }
            throw new BizException(switch (cur.getStatus()) {
                case "待领取" -> "任务还未领取，请先领取再提交";
                case "待验收" -> "任务已提交，正等待经理验收，请勿重复提交";
                default -> "任务当前状态为「" + cur.getStatus() + "」，不能提交";
            });
        }
        CleaningTask saved = reload(taskId);
        addRecord(taskId, "提交", cleaner,
                "第 " + saved.getRound() + " 轮清场提交：卫生" + saved.getHygieneResult()
                        + "；遗留物：" + orNone(saved.getLeftoverItems())
                        + "；设备异常：" + orNone(saved.getEquipmentIssue()));
        return get(taskId);
    }

    // ---------------- 经理：验收放行 / 退回整改 / 异常处置 / 停用结论 ----------------

    /**
     * 验收放行：任务 → 已放行，包厢 → 空闲（恢复可接待）。
     * 带设备异常且未处置的任务在条件 UPDATE 层面直接不命中，卫生合格也放不出去。
     */
    @Transactional
    public CleaningTask accept(Long taskId, String manager, String note) {
        String mgr = requireName(manager, "验收经理姓名不能为空");
        CleaningTask cur = lock(taskId);
        rejectIfIssueOpen(cur);
        int updated = cleaningTaskRepository.acceptIfReleasable(taskId, mgr,
                trimToNull(note), LocalDateTime.now());
        if (updated == 0) {
            rejectIfIssueOpen(cur);
            if ("已放行".equals(cur.getStatus())) {
                throw new BizException("该任务已由「" + cur.getManagerName() + "」验收放行，请勿重复操作");
            }
            throw new BizException("任务当前状态为「" + cur.getStatus() + "」，不能验收放行");
        }
        // 闭环唯一出口：验收通过，包厢恢复空闲可接待
        Room room = roomRepository.findByIdForUpdate(cur.getRoomId())
                .orElseThrow(() -> new BizException("包厢不存在：" + cur.getRoomId()));
        room.setStatus("空闲");
        roomRepository.save(room);
        addRecord(taskId, "验收放行", mgr,
                note == null || note.isBlank() ? "验收合格，包厢恢复空闲可接待" : note.trim());
        return get(taskId);
    }

    /** 退回整改：必须填写整改要求；流水里保留前一轮问题快照，整改记录永不丢失 */
    @Transactional
    public CleaningTask returnForRework(Long taskId, String manager, String note) {
        String mgr = requireName(manager, "验收经理姓名不能为空");
        if (note == null || note.isBlank()) {
            throw new BizException("退回整改必须填写整改要求，便于保洁按单返工");
        }
        CleaningTask cur = lock(taskId);
        int updated = cleaningTaskRepository.returnIfPendingAccept(taskId, mgr, note.trim());
        if (updated == 0) {
            if ("已放行".equals(cur.getStatus())) {
                throw new BizException("该任务已由「" + cur.getManagerName() + "」验收放行，不能退回");
            }
            throw new BizException("任务当前状态为「" + cur.getStatus() + "」，不能退回整改");
        }
        // cur 是退回前锁读的当轮内容 —— 把前一次问题与本次整改要求一并留痕
        addRecord(taskId, "退回整改", mgr,
                "整改要求：" + note.trim()
                        + "。前次提交（第 " + cur.getRound() + " 轮）：卫生" + orNone(cur.getHygieneResult())
                        + "；遗留物：" + orNone(cur.getLeftoverItems())
                        + "；设备异常：" + orNone(cur.getEquipmentIssue()));
        return get(taskId);
    }

    /** 异常处置：登记设备异常的处理结果（如已联系工程维修、已更换麦克风），处置后才允许放行 */
    @Transactional
    public CleaningTask resolveIssue(Long taskId, String operator, String resolution) {
        String op = requireName(operator, "处置人姓名不能为空");
        if (resolution == null || resolution.isBlank()) {
            throw new BizException("异常处置必须填写处理结果");
        }
        CleaningTask cur = lock(taskId);
        int updated = cleaningTaskRepository.resolveIssueIfOpen(taskId, resolution.trim());
        if (updated == 0) {
            if (!Boolean.TRUE.equals(cur.getHasEquipmentIssue())) {
                throw new BizException("该任务没有上报设备异常，无需处置");
            }
            if (Boolean.TRUE.equals(cur.getIssueResolved())) {
                throw new BizException("设备异常已处置（" + cur.getIssueResolution() + "），请勿重复提交");
            }
            throw new BizException("任务当前状态为「" + cur.getStatus() + "」，不能登记异常处置");
        }
        addRecord(taskId, "异常处置", op,
                "设备异常「" + cur.getEquipmentIssue() + "」处置结果：" + resolution.trim());
        return get(taskId);
    }

    /** 停用结论：设备异常无法即时处置时，经理留下明确结论，任务 → 已停用，包厢 → 停用 */
    @Transactional
    public CleaningTask decommission(Long taskId, String manager, String conclusion) {
        String mgr = requireName(manager, "经理姓名不能为空");
        if (conclusion == null || conclusion.isBlank()) {
            throw new BizException("停用必须留下明确结论（原因与后续安排）");
        }
        CleaningTask cur = lock(taskId);
        int updated = cleaningTaskRepository.decommissionIfOpen(taskId, mgr,
                conclusion.trim(), LocalDateTime.now());
        if (updated == 0) {
            if ("已放行".equals(cur.getStatus())) {
                throw new BizException("该任务已验收放行，不能再作停用结论");
            }
            throw new BizException("任务当前状态为「" + cur.getStatus() + "」，不能作停用结论");
        }
        Room room = roomRepository.findByIdForUpdate(cur.getRoomId())
                .orElseThrow(() -> new BizException("包厢不存在：" + cur.getRoomId()));
        room.setStatus("停用");
        roomRepository.save(room);
        addRecord(taskId, "停用", mgr,
                "停用结论：" + conclusion.trim()
                        + (Boolean.TRUE.equals(cur.getHasEquipmentIssue())
                            ? "（关联设备异常：" + cur.getEquipmentIssue() + "）" : ""));
        return get(taskId);
    }

    // ---------------- 内部工具 ----------------

    private void rejectIfIssueOpen(CleaningTask task) {
        if ("待验收".equals(task.getStatus())
                && Boolean.TRUE.equals(task.getHasEquipmentIssue())
                && !Boolean.TRUE.equals(task.getIssueResolved())) {
            throw new BizException("存在未处置的设备异常（" + task.getEquipmentIssue()
                    + "），即使卫生合格也不能放行：请先「异常处置」登记处理结果，或作「停用结论」");
        }
    }

    /** 锁定并当前读任务行：并发写操作在此串行，且读到的必然是最新已提交状态 */
    private CleaningTask lock(Long taskId) {
        return cleaningTaskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new BizException("清场任务不存在：" + taskId));
    }

    /** 读本事务刚改完的任务（同事务内可见自己的 UPDATE） */
    private CleaningTask reload(Long taskId) {
        return cleaningTaskRepository.findById(taskId)
                .orElseThrow(() -> new BizException("清场任务不存在：" + taskId));
    }

    private void addRecord(Long taskId, String action, String operator, String detail) {
        CleaningRecord r = new CleaningRecord();
        r.setTaskId(taskId);
        r.setAction(action);
        r.setOperatorName(operator);
        r.setDetail(detail);
        r.setCreatedAt(LocalDateTime.now());
        cleaningRecordRepository.save(r);
    }

    private String requireName(String name, String message) {
        if (name == null || name.isBlank()) {
            throw new BizException(message);
        }
        return name.trim();
    }

    private String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String blankToDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s.trim();
    }

    private String orNone(String s) {
        return (s == null || s.isBlank()) ? "无" : s;
    }
}
