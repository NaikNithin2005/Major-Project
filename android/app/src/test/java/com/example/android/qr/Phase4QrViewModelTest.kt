package com.example.android.qr

import com.example.android.domain.classifier.DefaultUrlClassifier
import com.example.android.domain.usecase.CheckCameraPermissionUseCase
import com.example.android.domain.usecase.ProcessQrCodeUseCase
import com.example.android.presentation.qr.CameraPermissionState
import com.example.android.presentation.qr.QrScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase4QrViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockQrRepository: Phase4QrPipelineTest.FakeQrAnalysisRepository
    private lateinit var mockThreatRepository: Phase4QrPipelineTest.FakeThreatHistoryRepository
    private lateinit var processQrCodeUseCase: ProcessQrCodeUseCase
    private lateinit var viewModel: QrScannerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockQrRepository = Phase4QrPipelineTest.FakeQrAnalysisRepository()
        mockThreatRepository = Phase4QrPipelineTest.FakeThreatHistoryRepository()
        val classifier = DefaultUrlClassifier()
        processQrCodeUseCase = ProcessQrCodeUseCase(
            urlClassifier = classifier,
            qrAnalysisRepository = mockQrRepository,
            threatHistoryRepository = mockThreatRepository
        )

        // Pass a mockable CheckCameraPermissionUseCase subclass or lambda
        val fakePermissionUseCase = FakeCheckCameraPermissionUseCase(true)

        viewModel = QrScannerViewModel(
            checkCameraPermissionUseCase = fakePermissionUseCase,
            processQrCodeUseCase = processQrCodeUseCase,
            qrAnalysisRepository = mockQrRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialPermissionStateGranted() {
        viewModel.checkPermission()
        assertEquals(CameraPermissionState.Granted, viewModel.permissionState.value)
    }

    @Test
    fun testToggleFlashState() {
        assertFalse(viewModel.isFlashEnabled.value)
        viewModel.toggleFlash()
        assertTrue(viewModel.isFlashEnabled.value)
        viewModel.toggleFlash()
        assertFalse(viewModel.isFlashEnabled.value)
    }

    @Test
    fun testZoomRatioConstraints() {
        assertEquals(1.0f, viewModel.zoomRatio.value, 0.01f)
        viewModel.setZoomRatio(2.5f)
        assertEquals(2.5f, viewModel.zoomRatio.value, 0.01f)
        viewModel.setZoomRatio(10.0f)
        assertEquals(5.0f, viewModel.zoomRatio.value, 0.01f)
    }

    @Test
    fun testQrCodeScanningSuccessFlow() = runTest {
        val payload = "https://safe-domain.com/login"
        viewModel.onQrCodeScanned(payload, source = "CAMERA")
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.scanResult.value
        assertNotNull(result)
        assertEquals("https://safe-domain.com/login", result?.extractedUrl)
        assertEquals("safe-domain.com", result?.domain)
        assertNotNull(viewModel.extractedFeatures.value)

        viewModel.dismissResult()
        assertNull(viewModel.scanResult.value)
        assertNull(viewModel.extractedFeatures.value)
    }

    @Test
    fun testQrCodeScanningBlankPayloadSetsError() = runTest {
        viewModel.onQrCodeScanned("   ", source = "CAMERA")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.scanResult.value)
        assertNotNull(viewModel.errorMessage.value)

        viewModel.clearError()
        assertNull(viewModel.errorMessage.value)
    }

    private class FakeCheckCameraPermissionUseCase(
        private val isGranted: Boolean
    ) : CheckCameraPermissionUseCase(FakeContext()) {
        override fun invoke(): Boolean = isGranted
    }

    private class FakeContext : android.content.ContextWrapper(null) {
        override fun checkPermission(permission: String, pid: Int, uid: Int): Int {
            return android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        override fun checkSelfPermission(permission: String): Int {
            return android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}
