package lv.putnik.core

import kotlin.test.*
import org.junit.Test

class CompanionTest {
    @Test fun russianInflectionsFindRelevantGuide() {
        assertEquals("fire", GuideLibrary.search("Как разжечь костёр после дождя?").first().id)
        assertEquals("lost", GuideLibrary.search("Я заблудился в лесу").first().id)
        assertEquals("water", GuideLibrary.search("Где взять воды?").first().id)
    }
    @Test fun fireRequiresEquipmentAndDoesNotAssumeNegatedLighterExists() {
        val c = Companion()
        val first = c.ask("Нет зажигалки, как развести огонь?")
        assertTrue(first.choices.contains("Ничего"))
        assertFalse(first.text.contains("используй зажигалку"))
        assertTrue(c.ask("Ничего").text.contains("Не рассчитывай на трение"))
    }
    @Test fun stepsRepeatAndStopHaveRealState() {
        val c = Companion(); val first = c.ask("Как найти воду?")
        assertEquals(first.guide, c.activeGuide)
        assertTrue(c.ask("дальше").text.startsWith("Шаг 2"))
        assertTrue(c.ask("повтори").text.startsWith("Шаг 2"))
        c.ask("стоп"); assertNull(c.activeGuide)
    }
    @Test fun emergencyOverridesFireOrPersonality() {
        val r = Companion().ask("У костра человек не дышит", Personality.ULTRA)
        assertEquals("first-aid",r.guide?.id); assertTrue(r.critical)
        assertFalse(r.text.contains("разберёмся"))
    }
    @Test fun unavailableKnowledgeDoesNotInventAnswer() {
        val r = Companion().ask("Как починить квантовый компьютер?")
        assertNull(r.guide); assertTrue(r.text.contains("не нашлось"))
    }
    @Test fun quantitativeHallucinationsAndUnknownCitationsAreRejected() {
        val c=Companion(); val g=GuideLibrary.byId("water")!!
        assertFalse(c.validModelAnswer("Добавь 10 капель [water]",g))
        assertFalse(c.validModelAnswer("Всё безопасно [made-up]",g))
        assertFalse(c.validModelAnswer("Без ссылки",g))
        assertTrue(c.validModelAnswer("Прозрачность не гарантирует безопасность [water]",g))
        assertFalse(c.validModelAnswer("Позвони специалисту [first-aid]",GuideLibrary.byId("first-aid")!!))
    }
    @Test fun sourceKeysAreUniqueAndHttps() {
        assertEquals(GuideLibrary.all.size,GuideLibrary.all.map { it.id }.toSet().size)
        GuideLibrary.all.forEach { g -> assertTrue(g.steps.isNotEmpty()); assertTrue(g.sources.isNotEmpty()); g.sources.forEach { assertTrue(it.url.startsWith("https://")) } }
    }
}
