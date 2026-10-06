package com.karaoke.lounge.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karaoke.lounge.common.ApiResponse;
import com.karaoke.lounge.entity.CleaningTask;
import com.karaoke.lounge.service.CleaningTaskService;

/**
 * 清场交接与验收放行接口。
 *
 * <p>列表支持 status（逗号分隔多值）与 roomId 过滤：
 * 保洁待办 = 待领取,清洁中,整改中；经理待验收 = 待验收；整改积压 = 整改中；
 * 包厢详情追溯 = roomId。</p>
 */
@RestController
@RequestMapping("/api/cleaning-tasks")
public class CleaningTaskController {

    private final CleaningTaskService cleaningTaskService;

    public CleaningTaskController(CleaningTaskService cleaningTaskService) {
        this.cleaningTaskService = cleaningTaskService;
    }

    @GetMapping
    public ApiResponse<List<CleaningTask>> list(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long roomId) {
        return ApiResponse.success(cleaningTaskService.list(status, roomId));
    }

    @GetMapping("/{id}")
    public ApiResponse<CleaningTask> get(@PathVariable Long id) {
        return ApiResponse.success(cleaningTaskService.get(id));
    }

    /** 保洁领取（仅待领取可领，并发领取只有一人成功） */
    @PostMapping("/{id}/claim")
    public ApiResponse<CleaningTask> claim(@PathVariable Long id, @RequestParam String operator) {
        return ApiResponse.success("已领取，开始清场", cleaningTaskService.claim(id, operator));
    }

    /** 保洁提交清场结果（卫生结果 / 遗留物 / 设备异常） */
    @PostMapping("/{id}/submit")
    public ApiResponse<CleaningTask> submit(@PathVariable Long id, @RequestBody CleaningTask input) {
        return ApiResponse.success("清场结果已提交，等待经理验收", cleaningTaskService.submit(id, input));
    }

    /** 经理验收放行：任务闭环，包厢恢复空闲 */
    @PostMapping("/{id}/accept")
    public ApiResponse<CleaningTask> accept(@PathVariable Long id, @RequestParam String manager,
                                            @RequestParam(required = false) String note) {
        return ApiResponse.success("验收通过，包厢已放行恢复空闲", cleaningTaskService.accept(id, manager, note));
    }

    /** 经理退回整改：必须填写整改要求，前一轮问题随流水保留 */
    @PostMapping("/{id}/return")
    public ApiResponse<CleaningTask> returnForRework(@PathVariable Long id, @RequestParam String manager,
                                                     @RequestParam String note) {
        return ApiResponse.success("已退回整改", cleaningTaskService.returnForRework(id, manager, note));
    }

    /** 设备异常处置登记：处置完成后才允许验收放行 */
    @PostMapping("/{id}/resolve-issue")
    public ApiResponse<CleaningTask> resolveIssue(@PathVariable Long id, @RequestParam String operator,
                                                  @RequestParam String note) {
        return ApiResponse.success("设备异常已登记处置", cleaningTaskService.resolveIssue(id, operator, note));
    }

    /** 经理停用结论：设备异常无法即时处置时停用包厢 */
    @PostMapping("/{id}/decommission")
    public ApiResponse<CleaningTask> decommission(@PathVariable Long id, @RequestParam String manager,
                                                  @RequestParam String conclusion) {
        return ApiResponse.success("已作停用结论，包厢停用", cleaningTaskService.decommission(id, manager, conclusion));
    }
}
