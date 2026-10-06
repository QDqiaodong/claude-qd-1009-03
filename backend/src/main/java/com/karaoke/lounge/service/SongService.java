package com.karaoke.lounge.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.entity.Song;
import com.karaoke.lounge.repository.SongRepository;

/**
 * 歌曲库业务。
 *
 * <p>规则：① 歌曲编码唯一；② 语种须为合法枚举；③ 时长须 &gt; 0。</p>
 */
@Service
public class SongService {

    private final SongRepository songRepository;

    public SongService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    /**
     * 歌曲列表查询。
     *
     * <p>语种筛选口径（三态明确，拒绝模糊入参）：</p>
     * <ul>
     *   <li>不传 language：返回全库（兼容无参调用）；</li>
     *   <li>language=全部：显式的“全部”，同样返回全库；</li>
     *   <li>language=国语/粤语/英文/日文：按语种过滤，无结果时返回空列表；</li>
     *   <li>language 为空白或其他非法值：抛业务异常（HTTP 400），
     *       与“筛选合法但查无结果”的空列表严格区分，排查时可直接定位为参数错误。</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<Song> list(String language) {
        if (language == null) {
            return songRepository.findAll();
        }
        String normalized = language.trim();
        if (normalized.isEmpty()) {
            throw new BizException("歌曲语种不能为空：请传合法语种（" + Dict.SONG_LANGUAGES
                    + "）或 “" + Dict.SONG_LANGUAGE_ALL + "”，不传该参数也表示全部");
        }
        if (Dict.SONG_LANGUAGE_ALL.equals(normalized)) {
            return songRepository.findAll();
        }
        if (!Dict.isValidSongLanguage(normalized)) {
            throw new BizException("歌曲语种不合法：" + language + "，应为 "
                    + Dict.SONG_LANGUAGES + " 或 " + Dict.SONG_LANGUAGE_ALL);
        }
        return songRepository.findByLanguage(normalized);
    }

    @Transactional(readOnly = true)
    public Song get(Long id) {
        return songRepository.findById(id)
                .orElseThrow(() -> new BizException("歌曲不存在：" + id));
    }

    @Transactional
    public Song create(Song input) {
        if (input.getSongCode() == null || input.getSongCode().isBlank()) {
            throw new BizException("歌曲编码不能为空");
        }
        if (songRepository.existsBySongCode(input.getSongCode().trim())) {
            throw new BizException("歌曲编码已存在：" + input.getSongCode());
        }
        if (input.getSongName() == null || input.getSongName().isBlank()) {
            throw new BizException("歌曲名称不能为空");
        }
        if (input.getLanguage() != null && !input.getLanguage().isBlank()
                && !Dict.isValidSongLanguage(input.getLanguage())) {
            throw new BizException("歌曲语种不合法：" + input.getLanguage() + "，应为 " + Dict.SONG_LANGUAGES);
        }
        if (input.getDurationSec() != null && input.getDurationSec() <= 0) {
            throw new BizException("歌曲时长必须大于 0");
        }
        Song song = new Song();
        song.setSongCode(input.getSongCode().trim());
        song.setSongName(input.getSongName().trim());
        song.setSinger(input.getSinger() == null ? "" : input.getSinger().trim());
        song.setLanguage(input.getLanguage() == null ? "国语" : input.getLanguage());
        song.setDurationSec(input.getDurationSec());
        return songRepository.save(song);
    }
}
