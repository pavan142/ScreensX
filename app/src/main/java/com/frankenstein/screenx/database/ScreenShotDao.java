package com.frankenstein.screenx.database;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Query;

@Dao
public interface ScreenShotDao {

    @Query("INSERT INTO ScreenShotEntity (filename, text_content, meta_data) values(:filename, :textContent, :metaData)")
    void putScreenShot(String filename, String textContent, String metaData);

    @Query("SELECT * FROM ScreenShotEntity")
    List<ScreenShotEntity> getAll();

    @Query ("SELECT * FROM ScreenShotEntity INNER JOIN fts ON ScreenShotEntity.`rowid` = fts.`rowid` WHERE fts.text_content MATCH :query")
    List<ScreenShotEntity> findByContent(String query);

    @Query("SELECT * FROM ScreenShotEntity WHERE filename LIKE :filename")
    ScreenShotEntity getScreenShotByName(String filename);

    @Query("DELETE FROM ScreenShotEntity WHERE filename LIKE :filename")
    void deleteScreenShot(String filename);

    @Query("DELETE FROM ScreenShotEntity WHERE filename in (:filenames)")
    void deleteMultipleScreenShots(String[] filenames);

    @Query("DELETE FROM ScreenShotEntity")
    void deleteAll();
}
