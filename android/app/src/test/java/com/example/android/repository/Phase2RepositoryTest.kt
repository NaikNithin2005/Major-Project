package com.example.android.repository

import com.example.android.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Phase2RepositoryTest {

    private inner class FakeThreatHistoryRepository : com.example.android.domain.repository.ThreatHistoryRepository {
        private val list = mutableListOf<ThreatRecord>()

        override suspend fun addThreat(threat: ThreatRecord): Result<Unit> {
            list.add(threat)
            return Result.success(Unit)
        }

        override suspend fun getThreatById(id: String): Result<ThreatRecord?> {
            return Result.success(list.find { it.id == id })
        }

        override fun observeAllThreats(): kotlinx.coroutines.flow.Flow<List<ThreatRecord>> {
            return kotlinx.coroutines.flow.flowOf(list.toList())
        }

        override suspend fun getAllThreats(): Result<List<ThreatRecord>> {
            return Result.success(list.toList())
        }

        override suspend fun updateThreat(threat: ThreatRecord): Result<Unit> {
            val index = list.indexOfFirst { it.id == threat.id }
            if (index != -1) {
                list[index] = threat
                return Result.success(Unit)
            }
            return Result.failure(IllegalArgumentException("Record not found"))
        }

        override suspend fun deleteThreat(id: String): Result<Unit> {
            list.removeAll { it.id == id }
            return Result.success(Unit)
        }

        override suspend fun clearAllThreats(): Result<Unit> {
            list.clear()
            return Result.success(Unit)
        }
    }

    @Test
    fun testThreatHistoryRepositoryCrud() = runBlocking {
        val repo = FakeThreatHistoryRepository()

        // 1. Create & Add
        val record = ThreatRecord(
            id = "rec_001",
            timestamp = System.currentTimeMillis(),
            source = "QR",
            sender = "evil-qr.com",
            riskScore = 92,
            category = "Quishing",
            actionTaken = "BLOCKED",
            details = "Malicious redirect detected"
        )
        val addResult = repo.addThreat(record)
        assertTrue(addResult.isSuccess)

        // 2. Read by ID
        val fetched = repo.getThreatById("rec_001").getOrNull()
        assertNotNull(fetched)
        assertEquals("Quishing", fetched?.category)

        // 3. Observe All
        val observed = repo.observeAllThreats().first()
        assertEquals(1, observed.size)

        // 4. Update
        val updated = record.copy(riskScore = 99)
        val updateResult = repo.updateThreat(updated)
        assertTrue(updateResult.isSuccess)
        assertEquals(99, repo.getThreatById("rec_001").getOrNull()?.riskScore)

        // 5. Delete
        val deleteResult = repo.deleteThreat("rec_001")
        assertTrue(deleteResult.isSuccess)
        assertNull(repo.getThreatById("rec_001").getOrNull())
    }

    @Test
    fun testMissingRecordBehaviorReturnsNull() = runBlocking {
        val repo = FakeThreatHistoryRepository()
        val result = repo.getThreatById("non_existent_id")
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }
}
