package com.remora.di

import android.content.Context
import com.remora.ai.AiGatewayClient
import com.remora.data.SaveCoordinator
import com.remora.work.WorkScheduler
import com.remora.core.model.contract.MemoryCardRepository
import com.remora.data.local.RemoraDatabase
import com.remora.data.repository.DefaultMemoryCardRepository
import com.remora.resurfacing.ResurfacingManager
import com.remora.resurfacing.Stage1CandidateSelector
import com.remora.resurfacing.Stage2BatchEvaluator

interface AppContainer {
    val database: RemoraDatabase
    val cardRepository: MemoryCardRepository
    val aiGatewayClient: AiGatewayClient
    val resurfacingManager: ResurfacingManager
    val saveCoordinator: SaveCoordinator
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val saveCoordinator: SaveCoordinator by lazy {
        SaveCoordinator(context, database, WorkScheduler(context))
    }

    override val database: RemoraDatabase by lazy {
        RemoraDatabase.getInstance(context)
    }

    override val cardRepository: MemoryCardRepository by lazy {
        DefaultMemoryCardRepository(database.memoryCardDao())
    }

    override val aiGatewayClient: AiGatewayClient by lazy {
        AiGatewayClient()
    }

    override val resurfacingManager: ResurfacingManager by lazy {
        val db = database
        val selector = Stage1CandidateSelector(db.memoryCardDao())
        val evaluator = Stage2BatchEvaluator(aiGatewayClient)
        ResurfacingManager(
            memoryCardDao = db.memoryCardDao(),
            resurfacingResultDao = db.resurfacingResultDao(),
            stage1Selector = selector,
            stage2Evaluator = evaluator
        )
    }
}
