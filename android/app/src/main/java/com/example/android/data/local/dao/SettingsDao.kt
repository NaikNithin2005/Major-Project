package com.example.android.data.local.dao

import androidx.room.*
import com.example.android.data.local.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingsEntity)

    @Query("SELECT * FROM settings WHERE settingKey = :key LIMIT 1")
    suspend fun getSettingByKey(key: String): SettingsEntity?

    @Query("SELECT * FROM settings WHERE settingKey = :key LIMIT 1")
    fun observeSettingByKey(key: String): Flow<SettingsEntity?>

    @Query("DELETE FROM settings WHERE settingKey = :key")
    suspend fun deleteSetting(key: String)
}
