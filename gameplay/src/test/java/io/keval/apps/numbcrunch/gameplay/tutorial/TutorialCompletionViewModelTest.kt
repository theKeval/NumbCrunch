package io.keval.apps.numbcrunch.gameplay.tutorial

import androidx.lifecycle.SavedStateHandle
import io.keval.apps.numbcrunch.domain.tutorial.TutorialEvidence
import io.keval.apps.numbcrunch.gameplay.game.TestProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TutorialCompletionViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private val completed = TutorialEvidence().visit(0).visit(1).visit(2).submit("245")

    @Test fun `final action pins owner before later Play Games authentication`() = runBlocking {
        val repository = TestProgressRepository()
        val vm = TutorialCompletionViewModel(repository, SavedStateHandle()) { 10 }
        repository.activatePlayerProfile("player-A")
        var navigated = 0
        vm.finish(completed) { navigated++ }
        assertEquals("guest", repository.tutorials.value.single().ownerId)
        assertEquals(1, navigated)
        vm.finish(completed) { navigated++ }
        assertEquals(1, repository.tutorials.value.size)
    }

    @Test fun `skip and incomplete practice award nothing but allow finishing`() {
        val repository = TestProgressRepository()
        val vm = TutorialCompletionViewModel(repository, SavedStateHandle()) { 10 }
        assertTrue(repository.tutorials.value.isEmpty())
        var navigated = false
        vm.finish(TutorialEvidence().visit(0).visit(1).visit(2).submit("123")) { navigated = true }
        assertTrue(navigated)
        assertTrue(repository.tutorials.value.isEmpty())
    }

    @Test fun `write failure does not leave and restoration retries same owner and timestamp`() = runBlocking {
        val repository = TestProgressRepository()
        val state = SavedStateHandle()
        val vm = TutorialCompletionViewModel(repository, state) { 10 }
        repository.failNextWrite = true
        var navigated = false
        vm.finish(completed) { navigated = true }
        assertTrue(vm.state.value.error)
        assertFalse(navigated)
        repository.activatePlayerProfile("player-A")
        val restored = TutorialCompletionViewModel(repository, state) { 20 }
        restored.finish(completed) { navigated = true }
        assertTrue(navigated)
        assertEquals("guest", repository.tutorials.value.single().ownerId)
        assertEquals(10L, repository.tutorials.value.single().completedAt)
    }
}
