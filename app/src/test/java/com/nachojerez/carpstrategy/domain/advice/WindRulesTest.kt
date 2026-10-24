package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.WindRelation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WindRulesTest {
    @Test
    fun `calido o frio respecto al agua`() {
        assertEquals(WindTemperature.WARM, WindRules.temperature(20.0, 17.0))
        assertEquals(WindTemperature.COLD, WindRules.temperature(12.0, 16.0))
        assertEquals(WindTemperature.NEUTRAL, WindRules.temperature(17.0, 16.0))
        assertNull(WindRules.temperature(null, 16.0))
    }

    @Test
    fun `viento calido de cara solo con agua por debajo del optimo y sostenido`() {
        assertTrue(WindRules.favours(WindRelation.FACING, 20.0, 16.0, 12.0, 0.8))
        assertFalse(WindRules.favours(WindRelation.FACING, 20.0, 16.0, 8.0, 0.8), "flojo")
        assertFalse(WindRules.favours(WindRelation.FACING, 20.0, 16.0, 12.0, 0.4), "racheado")
        assertFalse(WindRules.favours(WindRelation.FACING, 30.0, 25.0, 12.0, 0.8), "agua ya caliente")
        assertFalse(WindRules.favours(WindRelation.FACING, 12.0, 16.0, 12.0, 0.8), "viento frío de cara")
        assertFalse(WindRules.favours(WindRelation.FACING, null, 16.0, 12.0, 0.8), "sin aire no se sabe")
    }

    @Test
    fun `viento frio favorece la orilla resguardada`() {
        assertTrue(WindRules.favours(WindRelation.BEHIND, 10.0, 15.0, 12.0))
        assertFalse(WindRules.favours(WindRelation.BEHIND, 20.0, 15.0, 12.0))
        assertFalse(WindRules.favours(WindRelation.SIDE, 10.0, 15.0, 12.0))
    }

    @Test
    fun `avisos practicos`() {
        assertEquals(listOf(WindTip.SHORT_CAST, WindTip.FALSE_BEEPS), WindRules.tips(WindRelation.FACING, 18.0, 25.0))
        assertEquals(listOf(WindTip.SLACK_LINE), WindRules.tips(WindRelation.SIDE, 12.0, null))
        assertEquals(listOf(WindTip.BAIT_FAR, WindTip.ANCHOR), WindRules.tips(WindRelation.BEHIND, 20.0, 45.0))
        assertTrue(WindRules.tips(WindRelation.CALM, 3.0, null).isEmpty())
    }
}
