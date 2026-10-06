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
import com.karaoke.lounge.entity.Song;
import com.karaoke.lounge.service.SongService;

/** 歌曲库接口 */
@RestController
@RequestMapping("/api/songs")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    @GetMapping
    public ApiResponse<List<Song>> list(@RequestParam(required = false) String language) {
        return ApiResponse.success(songService.list(language));
    }

    @GetMapping("/{id}")
    public ApiResponse<Song> get(@PathVariable Long id) {
        return ApiResponse.success(songService.get(id));
    }

    @PostMapping
    public ApiResponse<Song> create(@RequestBody Song input) {
        return ApiResponse.success("歌曲已入库", songService.create(input));
    }
}
