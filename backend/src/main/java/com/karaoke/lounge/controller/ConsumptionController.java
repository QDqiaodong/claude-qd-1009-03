package com.karaoke.lounge.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karaoke.lounge.common.ApiResponse;
import com.karaoke.lounge.dto.SettlementResult;
import com.karaoke.lounge.entity.Consumption;
import com.karaoke.lounge.service.ConsumptionService;

/** 消费接口（含最低消费、储值余额原子结账校验） */
@RestController
@RequestMapping("/api/consumptions")
public class ConsumptionController {

    private final ConsumptionService consumptionService;

    public ConsumptionController(ConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @GetMapping
    public ApiResponse<List<Consumption>> list(@RequestParam(required = false) Long bookingId) {
        return ApiResponse.success(consumptionService.listByBooking(bookingId));
    }

    @PostMapping
    public ApiResponse<Consumption> create(@RequestBody Consumption input) {
        return ApiResponse.success("消费已记录", consumptionService.create(input));
    }

    /**
     * 结账开单。paymentMethod 可选：前台现付（默认）/ 储值余额。
     * 同时兼容 JSON body 和 query 参数，便于页面明确提交所选结算方式。
     */
    @PostMapping("/settle")
    public ApiResponse<SettlementResult> settle(@RequestParam Long bookingId,
                                                @RequestParam(required = false) String paymentMethod,
                                                @RequestBody(required = false) SettlementRequest body) {
        String method = paymentMethod != null ? paymentMethod : (body == null ? null : body.paymentMethod());
        SettlementResult result = consumptionService.settle(bookingId, method);
        String message = "结账成功，合计 " + result.totalAmount() + " 元（"
                + result.paymentMethod() + "）";
        if (result.memberBalanceAfter() != null) {
            message += "，扣款后余额 " + result.memberBalanceAfter() + " 元，流水#" + result.transactionId();
        }
        return ApiResponse.success(message, result);
    }

    /** 结账请求体。 */
    public record SettlementRequest(String paymentMethod) {
    }
}
