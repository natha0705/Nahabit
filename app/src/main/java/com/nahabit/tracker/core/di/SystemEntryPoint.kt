package com.nahabit.tracker.core.di

import com.nahabit.tracker.core.database.NahabitDao
import com.nahabit.tracker.core.time.TimeProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SystemEntryPoint {
    fun dao(): NahabitDao
    fun timeProvider(): TimeProvider
}
