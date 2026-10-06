package com.karaoke.lounge.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.karaoke.lounge.entity.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByRoom_Id(Long roomId);

    List<Booking> findByMemberId(Long memberId);

    List<Booking> findByStatus(String status);

    List<Booking> findByRoom_IdAndStatusNot(Long roomId, String status);

    List<Booking> findByRoom_IdAndStatus(Long roomId, String status);

    @Query("SELECT b FROM Booking b WHERE b.memberId = ?1 AND b.status = ?2 ORDER BY b.settledAt DESC, b.id DESC")
    List<Booking> findByMemberIdAndStatusOrderBySettledAtDescIdDesc(Long memberId, String status);

    /** 结账时锁定消费单行，防止重复结账与并发写入。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = ?1")
    Optional<Booking> findForUpdateById(Long id);

    /** 该包厢最近一场未取消的预订（结束使用生成清场任务时关联场次用） */
    Booking findTopByRoom_IdAndStatusNotOrderByEndAtDesc(Long roomId, String status);
}
