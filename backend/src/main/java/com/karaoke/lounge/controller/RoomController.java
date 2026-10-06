package com.karaoke.lounge.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karaoke.lounge.common.ApiResponse;
import com.karaoke.lounge.entity.CleaningTask;
import com.karaoke.lounge.entity.Room;
import com.karaoke.lounge.service.CleaningTaskService;
import com.karaoke.lounge.service.RoomService;

/** 包厢接口 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final CleaningTaskService cleaningTaskService;

    public RoomController(RoomService roomService, CleaningTaskService cleaningTaskService) {
        this.roomService = roomService;
        this.cleaningTaskService = cleaningTaskService;
    }

    @GetMapping
    public ApiResponse<List<Room>> list() {
        return ApiResponse.success(roomService.list());
    }

    /** 列出包厢及其预订：一次性子查询拉取所有包厢的预订，避免 N+1 */
    @GetMapping("/with-bookings")
    public ApiResponse<List<Room>> listWithBookings() {
        return ApiResponse.success("包厢及其预订（SUBSELECT 一次性拉取）", roomService.listWithBookings());
    }

    @GetMapping("/{id}")
    public ApiResponse<Room> get(@PathVariable Long id) {
        return ApiResponse.success(roomService.get(id));
    }

    @PostMapping
    public ApiResponse<Room> create(@RequestBody Room input) {
        return ApiResponse.success("包厢已建档", roomService.create(input));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Room> updateStatus(@PathVariable Long id, @RequestParam String target) {
        return ApiResponse.success("包厢状态已更新", roomService.updateStatus(id, target));
    }

    /** 结束使用：包厢 使用中 → 待清场，生成待领取的清场任务（闭环入口） */
    @PostMapping("/{id}/finish")
    public ApiResponse<CleaningTask> finish(@PathVariable Long id,
                                            @RequestParam(required = false) String operator) {
        return ApiResponse.success("已结束使用并生成待清场任务，验收放行前不可接待",
                cleaningTaskService.finishSession(id, operator));
    }
}
