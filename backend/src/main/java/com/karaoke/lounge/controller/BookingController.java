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
import com.karaoke.lounge.entity.Booking;
import com.karaoke.lounge.service.BookingService;

/** 预订接口（含人数/时段/冲突校验） */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ApiResponse<List<Booking>> list(@RequestParam(required = false) Long roomId,
                                           @RequestParam(required = false) Long memberId,
                                           @RequestParam(required = false) String status) {
        return ApiResponse.success(bookingService.list(roomId, memberId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Booking> get(@PathVariable Long id) {
        return ApiResponse.success(bookingService.get(id));
    }

    @PostMapping
    public ApiResponse<Booking> create(@RequestBody Booking input) {
        return ApiResponse.success("预订成功", bookingService.create(input));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Booking> cancel(@PathVariable Long id) {
        return ApiResponse.success("预订已取消", bookingService.cancel(id));
    }
}
