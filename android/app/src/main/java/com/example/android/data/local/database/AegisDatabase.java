package com.example.android.data.local.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.example.android.data.local.dao.*;
import com.example.android.data.local.entity.*;

@Database(
    entities = {
        UserEntity.class,
        ThreatHistoryEntity.class,
        SMSAnalysisEntity.class,
        QRAnalysisEntity.class,
        FeedbackEntity.class,
        SettingsEntity.class,
        ModelVersionEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class AegisDatabase extends RoomDatabase {

    public abstract UserDao userDao();
    public abstract ThreatHistoryDao threatHistoryDao();
    public abstract SMSAnalysisDao smsAnalysisDao();
    public abstract QRAnalysisDao qrAnalysisDao();
    public abstract FeedbackDao feedbackDao();
    public abstract SettingsDao settingsDao();
    public abstract ModelVersionDao modelVersionDao();

    private static volatile AegisDatabase INSTANCE;

    public static AegisDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AegisDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AegisDatabase.class,
                        "aegis_shield.db"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
