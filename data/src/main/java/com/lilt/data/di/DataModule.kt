package com.lilt.data.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.lilt.data.auth.FirebasePhoneAuthRepository
import com.lilt.data.chat.FirestoreChatRepository
import com.lilt.data.local.LiltDatabase
import com.lilt.data.local.MessageDao
import com.lilt.data.local.ThreadDao
import com.lilt.domain.auth.PhoneAuthRepository
import com.lilt.domain.chat.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindPhoneAuthRepository(repository: FirebasePhoneAuthRepository): PhoneAuthRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(repository: FirestoreChatRepository): ChatRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LiltDatabase =
        Room.databaseBuilder(
            context,
            LiltDatabase::class.java,
            "lilt.db",
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideThreadDao(database: LiltDatabase): ThreadDao = database.threadDao()

    @Provides
    fun provideMessageDao(database: LiltDatabase): MessageDao = database.messageDao()
}

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
