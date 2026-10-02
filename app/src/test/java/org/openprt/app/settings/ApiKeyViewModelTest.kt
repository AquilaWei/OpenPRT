package org.openprt.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.data.settings.ApiKeySettings
import org.openprt.app.data.truetime.ApiKeyChecker
import org.openprt.app.data.truetime.KeyCheck
import org.openprt.app.data.truetime.TrueTimeError

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ApiKeyViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_firstLaunchWithoutKey_showsScreen() {
        val viewModel = ApiKeyViewModel(settings(builtInKey = ""), FakeChecker(KeyCheck.Valid))

        assertTrue(viewModel.state.value.visible)
    }

    @Test
    fun state_builtInKey_hidesScreen() {
        val viewModel =
            ApiKeyViewModel(settings(builtInKey = "built-in"), FakeChecker(KeyCheck.Valid))

        assertFalse(viewModel.state.value.visible)
    }

    @Test
    fun state_skippedOnEarlierLaunch_hidesScreen() {
        settings(builtInKey = "").skip()

        val viewModel = ApiKeyViewModel(settings(builtInKey = ""), FakeChecker(KeyCheck.Valid))

        assertFalse(viewModel.state.value.visible)
    }

    @Test
    fun submit_validKey_checksTheEnteredKey() = runTest(dispatcher) {
        val checker = FakeChecker(KeyCheck.Valid)
        val viewModel = ApiKeyViewModel(settings(builtInKey = ""), checker)
        viewModel.onInputChanged(" good-key ")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf("good-key"), checker.checkedKeys)
    }

    @Test
    fun submit_validKey_savesKeyAndHidesScreen() = runTest(dispatcher) {
        val settings = settings(builtInKey = "")
        val viewModel = ApiKeyViewModel(settings, FakeChecker(KeyCheck.Valid))
        viewModel.onInputChanged("good-key")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("good-key", settings.currentKey())
        assertFalse(viewModel.state.value.visible)
    }

    @Test
    fun submit_whileChecking_isChecking() = runTest(dispatcher) {
        val checker = FakeChecker(CompletableDeferred())
        val viewModel = ApiKeyViewModel(settings(builtInKey = ""), checker)
        viewModel.onInputChanged("good-key")

        viewModel.submit()
        runCurrent()

        assertEquals(KeyCheckStatus.Checking, viewModel.state.value.check)
    }

    @Test
    fun submit_rejectedKey_showsRejectionAndSavesNothing() = runTest(dispatcher) {
        val settings = settings(builtInKey = "")
        val viewModel = ApiKeyViewModel(
            settings,
            FakeChecker(KeyCheck.Rejected(listOf("Invalid API access key supplied")))
        )
        viewModel.onInputChanged("bad-key")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(
            KeyCheckStatus.Rejected(listOf("Invalid API access key supplied")),
            viewModel.state.value.check
        )
        assertEquals("", settings.currentKey())
    }

    @Test
    fun submit_trueTimeUnreachable_offersSavingUncheckedAndSavesNothing() = runTest(dispatcher) {
        val settings = settings(builtInKey = "")
        val viewModel = ApiKeyViewModel(
            settings,
            FakeChecker(KeyCheck.Unreachable(TrueTimeError.Network(IOException("offline"))))
        )
        viewModel.onInputChanged("some-key")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(KeyCheckStatus.Unreachable, viewModel.state.value.check)
        assertEquals("", settings.currentKey())
    }

    @Test
    fun saveWithoutChecking_savesKeyAndHidesScreen() = runTest(dispatcher) {
        val settings = settings(builtInKey = "")
        val viewModel = ApiKeyViewModel(
            settings,
            FakeChecker(KeyCheck.Unreachable(TrueTimeError.Network(IOException("offline"))))
        )
        viewModel.onInputChanged("some-key")
        viewModel.submit()
        advanceUntilIdle()

        viewModel.saveWithoutChecking()

        assertEquals("some-key", settings.currentKey())
        assertFalse(viewModel.state.value.visible)
    }

    @Test
    fun submit_blankInput_checksNothing() = runTest(dispatcher) {
        val checker = FakeChecker(KeyCheck.Valid)
        val viewModel = ApiKeyViewModel(settings(builtInKey = ""), checker)
        viewModel.onInputChanged("   ")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), checker.checkedKeys)
    }

    @Test
    fun dismiss_onFirstLaunch_skipsOnboardingForNextLaunch() {
        val viewModel = ApiKeyViewModel(settings(builtInKey = ""), FakeChecker(KeyCheck.Valid))

        viewModel.dismiss()

        assertFalse(settings(builtInKey = "").state.value.needsOnboarding)
    }

    @Test
    fun dismiss_onFirstLaunch_hidesScreen() {
        val viewModel = ApiKeyViewModel(settings(builtInKey = ""), FakeChecker(KeyCheck.Valid))

        viewModel.dismiss()

        assertFalse(viewModel.state.value.visible)
    }

    @Test
    fun open_fromHomeScreen_showsScreenForChangingKey() {
        val viewModel =
            ApiKeyViewModel(settings(builtInKey = "built-in"), FakeChecker(KeyCheck.Valid))

        viewModel.open()

        assertEquals(
            ApiKeyUiState(visible = true, firstRun = false, hasKey = true),
            viewModel.state.value
        )
    }

    @Test
    fun dismiss_afterOpenWithTypedKey_keepsSavedKey() {
        val settings = settings(builtInKey = "")
        settings.save("old-key")
        val viewModel = ApiKeyViewModel(settings, FakeChecker(KeyCheck.Valid))
        viewModel.open()
        viewModel.onInputChanged("half-typed")

        viewModel.dismiss()

        assertEquals("old-key", settings.currentKey())
    }

    private fun settings(builtInKey: String) = ApiKeySettings(
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(ApiKeySettings.PREFS_NAME, Context.MODE_PRIVATE),
        builtInKey
    )

    private class FakeChecker(private val answer: CompletableDeferred<KeyCheck>) : ApiKeyChecker {
        constructor(check: KeyCheck) : this(CompletableDeferred(check))

        val checkedKeys = mutableListOf<String>()

        override suspend fun check(key: String): KeyCheck {
            checkedKeys += key
            return answer.await()
        }
    }
}
