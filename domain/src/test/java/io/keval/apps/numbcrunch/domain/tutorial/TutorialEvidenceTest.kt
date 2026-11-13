package io.keval.apps.numbcrunch.domain.tutorial

import org.junit.Assert.*
import org.junit.Test

class TutorialEvidenceTest {
    @Test fun `only all pages and solved practice produce a completion receipt`() {
        val visited = TutorialEvidence().visit(0).visit(1).visit(2)
        assertNull(visited.completion("owner", 10))
        assertNull(visited.submit("123").completion("owner", 10))
        assertNotNull(visited.submit("245").completion("owner", 10))
        assertNull(TutorialEvidence().visit(0).visit(2).submit("245").completion("owner", 10))
    }

    @Test fun `practice success and page visits remain true on revisits`() {
        val evidence = TutorialEvidence().visit(2).submit("245").submit("123").visit(0).visit(1).visit(0)
        assertEquals(7, evidence.visitedPages)
        assertEquals("owner", evidence.completion("owner", 10)?.ownerId)
        assertEquals(10L, evidence.completion("owner", 10)?.completedAt)
    }
}
