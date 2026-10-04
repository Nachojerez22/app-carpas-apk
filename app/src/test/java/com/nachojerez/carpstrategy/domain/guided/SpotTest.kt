package com.nachojerez.carpstrategy.domain.guided

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpotTest {
    @Test
    fun `viento que viene de donde mira la orilla es de cara`() {
        // Orilla mirando al oeste (270) y viento del oeste: sopla desde el agua hacia ti.
        assertEquals(WindRelation.FACING, Spots.windRelation(270, 270.0, 15.0))
        assertEquals(WindRelation.FACING, Spots.windRelation(0, 350.0, 15.0))
        assertEquals(WindRelation.FACING, Spots.windRelation(0, 45.0, 15.0))
    }

    @Test
    fun `viento contrario es de espaldas y el resto lateral`() {
        assertEquals(WindRelation.BEHIND, Spots.windRelation(270, 90.0, 15.0))
        assertEquals(WindRelation.BEHIND, Spots.windRelation(0, 200.0, 15.0))
        assertEquals(WindRelation.SIDE, Spots.windRelation(270, 0.0, 15.0))
        assertEquals(WindRelation.SIDE, Spots.windRelation(270, 180.0, 15.0))
    }

    @Test
    fun `viento flojo o datos que faltan`() {
        assertEquals(WindRelation.CALM, Spots.windRelation(270, 270.0, Spots.CALM_WIND_KMH - 0.1))
        assertNull(Spots.windRelation(null, 270.0, 15.0))
        assertNull(Spots.windRelation(270, null, 15.0))
        assertNull(Spots.windRelation(270, 270.0, null))
    }

    @Test
    fun `angulo entre rumbos y siguiente id`() {
        assertEquals(20.0, Spots.angleBetween(350.0, 10.0), 1e-9)
        assertEquals(180.0, Spots.angleBetween(90.0, 270.0), 1e-9)
        assertEquals(1, Spots.nextId(emptyList()))
        assertEquals(8, Spots.nextId(listOf(Spot(3, "a"), Spot(7, "b"))))
    }
}
