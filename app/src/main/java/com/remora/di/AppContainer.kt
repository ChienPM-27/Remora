package com.remora.di

import android.content.Context
import com.remora.core.model.contract.MemoryCardRepository
import com.remora.data.local.RemoraDatabase
import com.remora.data.repository.DefaultMemoryCardRepository

interface AppContainer {
    val database: RemoraDatabase
    val cardRepository: MemoryCardRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: RemoraDatabase by lazy {
        RemoraDatabase.getInstance(context)
    }

    override val cardRepository: MemoryCardRepository by lazy {
        DefaultMemoryCardRepository(database.memoryCardDao())
    }
}
