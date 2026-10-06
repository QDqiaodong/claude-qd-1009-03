package com.karaoke.lounge.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.entity.Booking;
import com.karaoke.lounge.entity.Room;
import com.karaoke.lounge.repository.BookingRepository;
import com.karaoke.lounge.repository.CleaningTaskRepository;
import com.karaoke.lounge.repository.RoomRepository;

/**
 * 预订业务。
 *
 * <p>核心规则（reject 分支）：
 * ① 预订人数不得超过包厢容量；
 * ② 预订时段必须整体落在营业窗口（11:00–次日 02:00）内；
 * ③ 同一包厢的预订不得时间重叠（已取消的预订不冲突）；
 * ④ 清场闭环取舍：待清场（未验收放行）/ 停用的包厢一律不可安排新场次 ——
 *    预订保留自己的业务状态，但包厢可用性同时受清场状态约束。</p>
 */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final CleaningTaskRepository cleaningTaskRepository;

    public BookingService(BookingRepository bookingRepository, RoomRepository roomRepository,
                          CleaningTaskRepository cleaningTaskRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.cleaningTaskRepository = cleaningTaskRepository;
    }

    @Transactional(readOnly = true)
    public List<Booking> list(Long roomId, Long memberId, String status) {
        if (status != null && !status.isBlank()) {
            return bookingRepository.findByStatus(status);
        }
        if (roomId != null) {
            return bookingRepository.findByRoom_Id(roomId);
        }
        if (memberId != null) {
            return bookingRepository.findByMemberId(memberId);
        }
        return bookingRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Booking get(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BizException("预订不存在：" + id));
    }

    @Transactional
    public Booking create(Booking input) {
        if (input.getRoomId() == null) {
            throw new BizException("必须指定包厢");
        }
        Room room = roomRepository.findById(input.getRoomId())
                .orElseThrow(() -> new BizException("包厢不存在：" + input.getRoomId()));
        // 规则④：清场闭环取舍 —— 未验收放行（待清场）或停用的包厢，任何时段都不安排新场次。
        // 宁可让下一场等验收，也不让未验收的包厢被排出去（对应「未验收不得对外显示可用」）。
        if ("待清场".equals(room.getStatus())) {
            throw new BizException("包厢 " + room.getRoomName()
                    + " 清场未验收，验收放行前不能安排新场次");
        }
        if ("停用".equals(room.getStatus())) {
            throw new BizException("包厢 " + room.getRoomName() + " 已停用（设备异常处置中），不能预订");
        }
        // 兜底：房态之外再核一次未闭环清场任务（防数据漂移）
        if (cleaningTaskRepository.existsByRoomIdAndStatusIn(room.getId(), Dict.CLEANING_OPEN_STATUSES)) {
            throw new BizException("包厢 " + room.getRoomName() + " 存在未闭环的清场任务，验收放行前不能安排新场次");
        }
        if (input.getMemberId() == null) {
            throw new BizException("必须指定会员");
        }
        if (input.getPartySize() == null) {
            throw new BizException("预订人数不能为空");
        }
        // 规则①：人数不超过容量
        if (!Dict.isPartySizeValid(input.getPartySize(), room.getCapacity())) {
            throw new BizException("预订人数 " + input.getPartySize()
                    + " 超过包厢 " + room.getRoomName() + " 的容量 " + room.getCapacity());
        }
        // 规则②：时段在营业窗口内
        if (!Dict.isBookingWindowValid(input.getStartAt(), input.getEndAt())) {
            throw new BizException("预订时段须整体落在营业时段（"
                    + Dict.OPEN_HOUR + ":00–次日 " + Dict.CLOSE_HOUR + ":00）内，且结束须晚于开始");
        }
        // 规则③：不与同包厢其它有效预订重叠
        List<Booking> others = bookingRepository.findByRoom_IdAndStatusNot(room.getId(), "已取消");
        for (Booking b : others) {
            if (Dict.overlaps(b.getStartAt(), b.getEndAt(), input.getStartAt(), input.getEndAt())) {
                throw new BizException("包厢 " + room.getRoomName() + " 在 "
                        + b.getStartAt() + "–" + b.getEndAt() + " 已被预订，时间冲突");
            }
        }
        Booking booking = new Booking();
        booking.setRoom(room);
        booking.setRoomId(room.getId());
        booking.setMemberId(input.getMemberId());
        booking.setStartAt(input.getStartAt());
        booking.setEndAt(input.getEndAt());
        booking.setPartySize(input.getPartySize());
        booking.setStatus("已预订");
        // 占用包厢
        if ("空闲".equals(room.getStatus())) {
            room.setStatus("预订");
            roomRepository.save(room);
        }
        return bookingRepository.save(booking);
    }

    /** 取消预订；若该包厢再无有效预订且当前为「预订」占用，则回置为空闲（使用中/待清场不动，走清场闭环） */
    @Transactional
    public Booking cancel(Long id) {
        // 锁定消费单，与结账互斥，避免取消操作覆盖已结账状态。
        Booking booking = bookingRepository.findForUpdateById(id)
                .orElseThrow(() -> new BizException("预订不存在：" + id));
        if ("已取消".equals(booking.getStatus())) {
            return booking;
        }
        booking.setStatus("已取消");
        Booking saved = bookingRepository.save(booking);
        Room room = booking.getRoom();
        boolean stillActive = !bookingRepository.findByRoom_IdAndStatusNot(room.getId(), "已取消").isEmpty();
        if (!stillActive && "预订".equals(room.getStatus())) {
            room.setStatus("空闲");
            roomRepository.save(room);
        }
        return saved;
    }
}
