package com.karaoke.lounge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.karaoke.lounge.entity.MemberTransaction;

public interface MemberTransactionRepository extends JpaRepository<MemberTransaction, Long> {

    @Query("SELECT t FROM MemberTransaction t WHERE t.memberId = ?1 ORDER BY t.createdAt DESC, t.id DESC")
    List<MemberTransaction> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);

    boolean existsByBookingId(Long bookingId);

    MemberTransaction findByBookingId(Long bookingId);
}
