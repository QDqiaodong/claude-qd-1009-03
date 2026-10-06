package com.karaoke.lounge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 歌曲库。
 *
 * <p>语种：国语 / 粤语 / 英文 / 日文；时长用于点歌排序与欢唱时长估算。</p>
 */
@Entity
@Table(name = "song")
@Getter
@Setter
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 歌曲编码，唯一，如 S001 */
    @Column(name = "song_code", nullable = false, unique = true, length = 16)
    private String songCode;

    @Column(name = "song_name", nullable = false, length = 64)
    private String songName;

    @Column(name = "singer", length = 32)
    private String singer;

    /** 国语 / 粤语 / 英文 / 日文 */
    @Column(name = "language", length = 16)
    private String language;

    /** 时长（秒） */
    @Column(name = "duration_sec")
    private Integer durationSec;
}
