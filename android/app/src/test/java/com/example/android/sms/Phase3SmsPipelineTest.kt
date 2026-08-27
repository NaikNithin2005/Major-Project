package com.example.android.sms

import com.example.android.data.parser.SmsParser
import com.example.android.data.preprocessor.SmsPreprocessor
import com.example.android.domain.classifier.DefaultSmishingClassifier
import com.example.android.domain.model.RawSms
import com.example.android.domain.model.SmsAnalysisResult
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.SmsAnalysisRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.domain.usecase.ProcessIncomingSmsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Phase3SmsPipelineTest {

    private lateinit var fakeSmsRepository: FakeSmsAnalysisRepository
    private lateinit var fakeThreatRepository: FakeThreatHistoryRepository
    private lateinit var useCase: ProcessIncomingSmsUseCase

    @Before
    fun setUp() {
        fakeSmsRepository = FakeSmsAnalysisRepository()
        fakeThreatRepository = FakeThreatHistoryRepository()
        useCase = ProcessIncomingSmsUseCase(
            smsParser = SmsParser(),
            smsPreprocessor = SmsPreprocessor(),
            classifier = DefaultSmishingClassifier(),
            smsAnalysisRepository = fakeSmsRepository,
            threatHistoryRepository = fakeThreatRepository
        )
    }

    @Test
    fun testSmishingMessagePipelineProcessing() = runBlocking {
        val smishingRaw = RawSms(
            messageId = "msg_101",
            sender = "AD-HDFCBK",
            body = "URGENT: Your HDFC bank account is suspended immediately. Click http://hdfc-update.com/login to verify.",
            timestamp = System.currentTimeMillis()
        )

        val result = useCase(smishingRaw)
        assertTrue(result.isSuccess)

        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertEquals("AD-HDFCBK", analysis!!.sender)
        assertTrue("Expected high risk score for smishing SMS", analysis.riskScore >= 50)
        assertTrue("Expected isSmishing to be true", analysis.isSmishing)
        assertEquals(1, analysis.extractedUrlsCount)

        // Verify database persistence in Fake repository
        assertEquals(1, fakeSmsRepository.savedAnalyses.size)
        assertEquals(1, fakeThreatRepository.savedThreats.size)
        assertEquals("SMS", fakeThreatRepository.savedThreats[0].source)
    }

    @Test
    fun testSafeMessagePipelineProcessing() = runBlocking {
        val safeRaw = RawSms(
            messageId = "msg_102",
            sender = "+919876543210",
            body = "Hey, are we still meeting for lunch today at 1 PM?",
            timestamp = System.currentTimeMillis()
        )

        val result = useCase(safeRaw)
        assertTrue(result.isSuccess)

        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertFalse("Expected safe SMS not to be flagged as smishing", analysis!!.isSmishing)
        assertTrue("Expected low risk score for safe SMS", analysis.riskScore < 50)
        assertEquals(0, analysis.extractedUrlsCount)

        // Verify safe SMS saved to history but no threat logged
        assertEquals(1, fakeSmsRepository.savedAnalyses.size)
        assertEquals(0, fakeThreatRepository.savedThreats.size)
    }

    // --- Fake Test Repositories ---

    private class FakeSmsAnalysisRepository : SmsAnalysisRepository {
        val savedAnalyses = mutableListOf<SmsAnalysisResult>()
        private val stateFlow = MutableStateFlow<List<SmsAnalysisResult>>(emptyList())

        override suspend fun saveAnalysis(result: SmsAnalysisResult): Result<Unit> {
            savedAnalyses.add(result)
            stateFlow.value = savedAnalyses.toList()
            return Result.success(Unit)
        }

        override suspend fun getAnalysisById(id: String): Result<SmsAnalysisResult?> {
            return Result.success(savedAnalyses.find { it.id == id })
        }

        override fun observeAllAnalyses(): Flow<List<SmsAnalysisResult>> = stateFlow

        override suspend fun deleteAnalysis(id: String): Result<Unit> {
            savedAnalyses.removeAll { it.id == id }
            stateFlow.value = savedAnalyses.toList()
            return Result.success(Unit)
        }
    }

    private class FakeThreatHistoryRepository : ThreatHistoryRepository {
        val savedThreats = mutableListOf<ThreatRecord>()
        private val stateFlow = MutableStateFlow<List<ThreatRecord>>(emptyList())

        override suspend fun addThreat(threat: ThreatRecord): Result<Unit> {
            savedThreats.add(threat)
            stateFlow.value = savedThreats.toList()
            return Result.success(Unit)
        }

        override suspend fun getThreatById(id: String): Result<ThreatRecord?> {
            return Result.success(savedThreats.find { it.id == id })
        }

        override fun observeAllThreats(): Flow<List<ThreatRecord>> = stateFlow

        override suspend fun getAllThreats(): Result<List<ThreatRecord>> {
            return Result.success(savedThreats)
        }

        override suspend fun updateThreat(threat: ThreatRecord): Result<Unit> {
            val idx = savedThreats.indexOfFirst { it.id == threat.id }
            if (idx != -1) savedThreats[idx] = threat
            stateFlow.value = savedThreats.toList()
            return Result.success(Unit)
        }

        override suspend fun deleteThreat(id: String): Result<Unit> {
            savedThreats.removeAll { it.id == id }
            stateFlow.value = savedThreats.toList()
            return Result.success(Unit)
        }

        override suspend fun clearAllThreats(): Result<Unit> {
            savedThreats.clear()
            stateFlow.value = emptyList()
            return Result.success(Unit)
        }
    }
}
