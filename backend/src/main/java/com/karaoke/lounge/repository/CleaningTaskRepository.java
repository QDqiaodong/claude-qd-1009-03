package com.karaoke.lounge.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karaoke.lounge.entity.CleaningTask;

import jakarta.persistence.LockModeType;

/**
 * 清场任务仓库。
 *
 * <p>所有状态迁移都是「条件 UPDATE … WHERE status = 期望前置状态」：
 * 行锁保证并发下只有一个事务能命中（返回 1），其余返回 0，
 * 由 Service 重读当前状态后抛出可理解的中文提示 —— 这是
 * 「两名保洁同时领取 / 多人重复验收只能有一个有效结果」的实现基础。</p>
 */
public interface CleaningTaskRepository extends JpaRepository<CleaningTask, Long> {

    List<CleaningTask> findAllByOrderByIdDesc();

    List<CleaningTask> findByStatusInOrderByIdDesc(List<String> statuses);

    List<CleaningTask> findByRoomIdOrderByIdDesc(Long roomId);

    List<CleaningTask> findByRoomIdAndStatusInOrderByIdDesc(Long roomId, List<String> statuses);

    boolean existsByRoomIdAndStatusIn(Long roomId, List<String> statuses);

    /**
     * 行级写锁 + 当前读：REPEATABLE READ 下普通 SELECT 走的是事务快照，
     * 看不到他人刚提交的修改；并发冲突后的提示必须读最新状态，
     * 因此所有写操作先 SELECT … FOR UPDATE 锁定并读到当前值。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM CleaningTask t WHERE t.id = :id")
    Optional<CleaningTask> findByIdForUpdate(@Param("id") Long id);

    /** 领取：仅「待领取」可领，原子写入负责人，防两名保洁同时领取 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CleaningTask t SET t.status = '清洁中', t.cleanerName = :cleaner, t.claimedAt = :now "
            + "WHERE t.id = :id AND t.status = '待领取'")
    int claimIfPending(@Param("id") Long id, @Param("cleaner") String cleaner, @Param("now") LocalDateTime now);

    /** 提交：仅负责人在「清洁中/整改中」可提交；每提交一轮轮次 +1，设备异常标记随本轮内容重置 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CleaningTask t SET t.status = '待验收', t.hygieneResult = :hygiene, "
            + "t.leftoverItems = :leftover, t.equipmentIssue = :issue, t.hasEquipmentIssue = :hasIssue, "
            + "t.issueResolved = false, t.issueResolution = NULL, t.submittedAt = :now, t.round = t.round + 1 "
            + "WHERE t.id = :id AND t.cleanerName = :cleaner AND t.status IN ('清洁中', '整改中')")
    int submitIfOwner(@Param("id") Long id, @Param("cleaner") String cleaner,
                      @Param("hygiene") String hygiene, @Param("leftover") String leftover,
                      @Param("issue") String issue, @Param("hasIssue") boolean hasIssue,
                      @Param("now") LocalDateTime now);

    /** 验收放行：仅「待验收」且无未处置设备异常可放行（设备异常未处置时该 UPDATE 不命中） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CleaningTask t SET t.status = '已放行', t.managerName = :manager, "
            + "t.acceptNote = :note, t.finishedAt = :now "
            + "WHERE t.id = :id AND t.status = '待验收' "
            + "AND (t.hasEquipmentIssue = false OR t.issueResolved = true)")
    int acceptIfReleasable(@Param("id") Long id, @Param("manager") String manager,
                           @Param("note") String note, @Param("now") LocalDateTime now);

    /** 退回整改：仅「待验收」可退回 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CleaningTask t SET t.status = '整改中', t.managerName = :manager, t.reworkNote = :note "
            + "WHERE t.id = :id AND t.status = '待验收'")
    int returnIfPendingAccept(@Param("id") Long id, @Param("manager") String manager,
                              @Param("note") String note);

    /** 异常处置：仅存在未处置设备异常且任务未闭环时可登记 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CleaningTask t SET t.issueResolved = true, t.issueResolution = :resolution "
            + "WHERE t.id = :id AND t.hasEquipmentIssue = true AND t.issueResolved = false "
            + "AND t.status IN ('清洁中', '待验收', '整改中')")
    int resolveIssueIfOpen(@Param("id") Long id, @Param("resolution") String resolution);

    /** 停用结论：仅「待验收/整改中」可由经理作停用结论 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CleaningTask t SET t.status = '已停用', t.managerName = :manager, "
            + "t.acceptNote = :conclusion, t.finishedAt = :now "
            + "WHERE t.id = :id AND t.status IN ('待验收', '整改中')")
    int decommissionIfOpen(@Param("id") Long id, @Param("manager") String manager,
                           @Param("conclusion") String conclusion, @Param("now") LocalDateTime now);
}
