package com.example.android.qr

import com.example.android.domain.classifier.DefaultUrlClassifier
import com.example.android.domain.model.QrAnalysisResult
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.QrAnalysisRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.domain.usecase.ProcessQrCodeUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Phase4QrPipelineTest {

    private lateinit var mockQrRepository: FakeQrAnalysisRepository
    private lateinit var mockThreatRepository: FakeThreatHistoryRepository
    private lateinit var classifier: DefaultUrlClassifier
    private lateinit var useCase: ProcessQrCodeUseCase

    @Before
    fun setUp() {
        mockQrRepository = FakeQrAnalysisRepository()
        mockThreatRepository = FakeThreatHistoryRepository()
        classifier = DefaultUrlClassifier()
        useCase = ProcessQrCodeUseCase(
            urlClassifier = classifier,
            qrAnalysisRepository = mockQrRepository,
            threatHistoryRepository = mockThreatRepository
        )
    }

    @Test
    fun testProcessValidSafeUrlQrCode() = runBlocking {
        val payload = "https://www.google.com/search?q=test"
        val result = useCase(payload, source = "CAMERA")

        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertEquals("https://www.google.com/search?q=test", analysis?.extractedUrl)
        assertEquals("google.com", analysis?.domain)
        assertFalse(analysis?.isQuishing == true)
        assertEquals(1, mockQrRepository.savedAnalyses.size)
        assertEquals(0, mockThreatRepository.savedThreats.size)
    }

    @Test
    fun testProcessSuspiciousIpUrlQrCode() = runBlocking {
        val payload = "http://192.168.1.100/admin/login?user=root@admin"
        val result = useCase(payload, source = "GALLERY")

        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertTrue(analysis!!.riskScore >= 50)
        assertTrue(analysis.isQuishing)
        assertEquals(1, mockQrRepository.savedAnalyses.size)
        assertEquals(1, mockThreatRepository.savedThreats.size)
        assertEquals("QUISHING", mockThreatRepository.savedThreats[0].category)
    }

    @Test
    fun testProcessPlainTextQrCode() = runBlocking {
        val payload = "WIFI:S:MyNetwork;P:Password123;;"
        val result = useCase(payload, source = "CAMERA")

        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()
        assertNotNull(analysis)
        assertEquals("", analysis?.extractedUrl)
        assertEquals("TEXT_PAYLOAD", analysis?.domain)
        assertFalse(analysis?.isQuishing == true)
        assertEquals(0, analysis?.riskScore)
    }

    @Test
    fun testProcessEmptyPayloadFails() = runBlocking {
        val result = useCase("   ", source = "CAMERA")
        assertTrue(result.isFailure)
    }

    // Fake Implementations for unit testing without database
    class FakeQrAnalysisRepository : QrAnalysisRepository {
        val savedAnalyses = mutableListOf<QrAnalysisResult>()
        private val _flow = MutableStateFlow<List<QrAnalysisResult>>(emptyList())

        override suspend fun saveAnalysis(result: QrAnalysisResult): Result<Unit> {
            savedAnalyses.add(result)
            _flow.value = savedAnalyses.toList()
            return Result.success(Unit)
        }

        override suspend fun getAnalysisById(id: String): Result<QrAnalysisResult?> {
            return Result.success(savedAnalyses.find { it.id == id })
        }

        override fun observeAllAnalyses(): Flow<List<QrAnalysisResult>> = _flow

        override suspend fun deleteAnalysis(id: String): Result<Unit> {
            savedAnalyses.removeIf { it.id == id }
            _flow.value = savedAnalyses.toList()
            return Result.success(Unit)
        }
    }

    class FakeThreatHistoryRepository : ThreatHistoryRepository {
        val savedThreats = mutableListOf<ThreatRecord>()
        private val _flow = MutableStateFlow<List<ThreatRecord>>(emptyList())

        override suspend fun addThreat(threat: ThreatRecord): Result<Unit> {
            savedThreats.add(threat)
            _flow.value = savedThreats.toList()
            return Result.success(Unit)
        }

        override suspend fun getThreatById(id: String): Result<ThreatRecord?> {
            return Result.success(savedThreats.find { it.id == id })
        }

        override fun observeAllThreats(): Flow<List<ThreatRecord>> = _flow

        override suspend fun getAllThreats(): Result<List<ThreatRecord>> {
            return Result.success(savedThreats.toList())
        }

        override suspend fun updateThreat(threat: ThreatRecord): Result<Unit> {
            val index = savedThreats.indexOfFirst { it.id == threat.id }
            if (index != -1) savedThreats[index] = threat
            _flow.value = savedThreats.toList()
            return Result.success(Unit)
        }

        override suspend fun deleteThreat(id: String): Result<Unit> {
            savedThreats.removeIf { it.id == id }
            _flow.value = savedThreats.toList()
            return Result.success(Unit)
        }

        override suspend fun clearAllThreats(): Result<Unit> {
            savedThreats.clear()
            _flow.value = emptyList()
            return Result.success(Unit)
        }
    }
}
