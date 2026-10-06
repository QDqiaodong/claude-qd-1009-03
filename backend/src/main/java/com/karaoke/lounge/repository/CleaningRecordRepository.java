package com.karaoke.lounge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karaoke.lounge.entity.CleaningRecord;

public interface CleaningRecordRepository extends JpaRepository<CleaningRecord, Long> {

    /** 批量取回一组任务的交接流水（按时间升序），避免逐任务查询的 N+1 */
    List<CleaningRecord> findByTaskIdInOrderByCreatedAtAscIdAsc(List<Long> taskIds);
}
