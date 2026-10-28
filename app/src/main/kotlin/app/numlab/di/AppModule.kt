package app.numlab.di

import android.content.Context
import androidx.room.Room
import app.numlab.data.db.NumLabDatabase
import app.numlab.data.prefs.DataStoreSettingsRepository
import app.numlab.data.prefs.numlabPrefs
import app.numlab.data.repo.RoomHistoryRepository
import app.numlab.domain.repo.HistoryRepository
import app.numlab.domain.repo.SettingsRepository
import app.numlab.ui.workbench.ComputeDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext ctx: Context): NumLabDatabase =
        Room.databaseBuilder(ctx, NumLabDatabase::class.java, "numlab.db").build()

    @Provides
    @Singleton
    fun history(db: NumLabDatabase, @ApplicationContext ctx: Context): HistoryRepository =
        RoomHistoryRepository(db, ctx.numlabPrefs)

    @Provides
    @Singleton
    fun settings(@ApplicationContext ctx: Context): SettingsRepository = DataStoreSettingsRepository(ctx.numlabPrefs)

    @Provides
    @ComputeDispatcher
    fun compute(): CoroutineDispatcher = Dispatchers.Default
}
