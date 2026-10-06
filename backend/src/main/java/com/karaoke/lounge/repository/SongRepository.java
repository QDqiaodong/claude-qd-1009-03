package com.karaoke.lounge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karaoke.lounge.entity.Song;

public interface SongRepository extends JpaRepository<Song, Long> {

    boolean existsBySongCode(String songCode);

    List<Song> findByLanguage(String language);
}
