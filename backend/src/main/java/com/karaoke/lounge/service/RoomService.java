package com.karaoke.lounge.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.entity.Room;
import com.karaoke.lounge.repository.CleaningTaskRepository;
import com.karaoke.lounge.repository.RoomRepository;

/**
 * 包厢业务。
 *
 * <p>规则：① 包厢编号唯一；② 容量须 &gt; 0、最低消费 &ge; 0；③ 状态必须是合法枚举。
 * 另提供「列出包厢及其预订」接口，演示 {@code SUBSELECT} 一次性拉取所有包厢的预订，避免 N+1。</p>
 *
 * <p>清场闭环取舍（明确）：选择「未验收不得对外显示可用」——
 * 使用中的包厢不能直接置回空闲，必须「结束使用」生成清场任务，经保洁提交、
 * 经理验收放行后由闭环自动恢复空闲；待清场 / 停用的包厢不可预订、不可投入使用。</p>
 */
@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final CleaningTaskRepository cleaningTaskRepository;

    public RoomService(RoomRepository roomRepository, CleaningTaskRepository cleaningTaskRepository) {
        this.roomRepository = roomRepository;
        this.cleaningTaskRepository = cleaningTaskRepository;
    }

    /**
     * 列出所有包厢（并触发 SUBSELECT 预载预订，避免后续序列化时懒加载失败）。
     */
    @Transactional(readOnly = true)
    public List<Room> list() {
        List<Room> rooms = roomRepository.findAll();
        initializeBookings(rooms);
        return rooms;
    }

    /**
     * 列出包厢及其预订：一次性子查询拉取所有包厢的预订集合，避免逐包厢查询（N+1）。
     */
    @Transactional(readOnly = true)
    public List<Room> listWithBookings() {
        List<Room> rooms = roomRepository.findAll();
        // 触发 SUBSELECT：对 findAll 加载的全部包厢，仅用一条子查询取回各自预订
        initializeBookings(rooms);
        return rooms;
    }

    private void initializeBookings(List<Room> rooms) {
        for (Room room : rooms) {
            room.getBookings().size(); // 访问集合即触发 SUBSELECT
        }
    }

    @Transactional(readOnly = true)
    public Room get(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new BizException("包厢不存在：" + id));
        room.getBookings().size();
        return room;
    }

    @Transactional
    public Room create(Room input) {
        if (input.getRoomCode() == null || input.getRoomCode().isBlank()) {
            throw new BizException("包厢编号不能为空");
        }
        if (roomRepository.existsByRoomCode(input.getRoomCode().trim())) {
            throw new BizException("包厢编号已存在：" + input.getRoomCode());
        }
        if (input.getRoomName() == null || input.getRoomName().isBlank()) {
            throw new BizException("包厢名称不能为空");
        }
        if (input.getCapacity() == null || input.getCapacity() <= 0) {
            throw new BizException("包厢容量必须大于 0");
        }
        if (input.getMinSpend() == null || input.getMinSpend().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("最低消费不能为负数");
        }
        String status = (input.getStatus() == null || input.getStatus().isBlank())
                ? "空闲" : input.getStatus();
        if (!Dict.isValidRoomStatus(status)) {
            throw new BizException("包厢状态不合法：" + status + "，应为 " + Dict.ROOM_STATUSES);
        }
        if ("待清场".equals(status)) {
            throw new BizException("「待清场」由结束使用自动生成，不能手工指定");
        }
        Room room = new Room();
        room.setRoomCode(input.getRoomCode().trim());
        room.setRoomName(input.getRoomName().trim());
        room.setCapacity(input.getCapacity());
        room.setMinSpend(input.getMinSpend());
        room.setStatus(status);
        return roomRepository.save(room);
    }

    /**
     * 手工改房态。闭环约束：
     * <ul>
     *   <li>使用中 → 空闲：禁止，须走「结束使用」进入清场闭环；</li>
     *   <li>待清场 → 任何状态：禁止，验收放行后自动回空闲；</li>
     *   <li>待清场：只能由「结束使用」生成，不能手工设置；</li>
     *   <li>停用 → 空闲：维修后恢复，前提是无未闭环清场任务。</li>
     * </ul>
     */
    @Transactional
    public Room updateStatus(Long id, String target) {
        Room room = get(id);
        if (!Dict.isValidRoomStatus(target)) {
            throw new BizException("包厢状态不合法：" + target + "，应为 " + Dict.ROOM_STATUSES);
        }
        String from = room.getStatus();
        if (from.equals(target)) {
            return room;
        }
        switch (target) {
            case "待清场" -> throw new BizException("「待清场」不能手工设置：请对使用中的包厢执行「结束使用」，系统会自动生成清场任务");
            case "空闲" -> {
                if ("使用中".equals(from)) {
                    throw new BizException("包厢正在使用，不能直接置空闲：请先「结束使用」生成清场任务，验收放行后自动恢复空闲");
                }
                if ("待清场".equals(from)) {
                    throw new BizException("包厢清场任务未闭环，须经理验收放行后才能恢复空闲");
                }
                // 预订 → 空闲（未开场的释放）、停用 → 空闲（维修后恢复）：须无未闭环清场任务
                if (cleaningTaskRepository.existsByRoomIdAndStatusIn(id, Dict.CLEANING_OPEN_STATUSES)) {
                    throw new BizException("该包厢存在未闭环的清场任务，验收放行前不能置为空闲");
                }
            }
            case "使用中" -> {
                if ("待清场".equals(from)) {
                    throw new BizException("包厢清场未验收，不能投入使用");
                }
                if ("停用".equals(from)) {
                    throw new BizException("包厢已停用，请先恢复为空闲再安排使用");
                }
            }
            case "预订" -> {
                if (!"空闲".equals(from)) {
                    throw new BizException("只有空闲包厢可以直接置为预订（当前「" + from + "」）");
                }
            }
            case "停用" -> {
                if ("待清场".equals(from)) {
                    throw new BizException("包厢清场任务未闭环：如需停用，请在清场任务中由经理作「停用结论」");
                }
            }
            default -> { /* 其余组合放行 */ }
        }
        room.setStatus(target);
        return roomRepository.save(room);
    }
}
