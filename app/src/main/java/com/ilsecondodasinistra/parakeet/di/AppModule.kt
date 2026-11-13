package com.ilsecondodasinistra.parakeet.di

import android.content.Context
import androidx.room.Room
import com.ilsecondodasinistra.parakeet.alarm.AlarmScheduler
import com.ilsecondodasinistra.parakeet.alarm.ClockAppScheduler
import com.ilsecondodasinistra.parakeet.data.AppDatabase
import com.ilsecondodasinistra.parakeet.data.TaskDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "parakeet.db").build()

    @Provides fun taskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides fun clock(): Clock = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmModule {
    @Binds abstract fun alarmScheduler(impl: ClockAppScheduler): AlarmScheduler
}
