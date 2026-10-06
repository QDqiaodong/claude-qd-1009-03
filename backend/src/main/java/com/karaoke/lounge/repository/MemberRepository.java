package com.karaoke.lounge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import com.karaoke.lounge.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByMemberNo(String memberNo);

    boolean existsByPhone(String phone);

    List<Member> findByLevel(String level);

    /** 储值扣款/充值时锁定会员行，串行化同一会员的余额变化。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = ?1")
    Optional<Member> findForUpdateById(Long id);
}
