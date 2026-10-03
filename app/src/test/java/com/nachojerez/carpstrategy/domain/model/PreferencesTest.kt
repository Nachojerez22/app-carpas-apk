package com.nachojerez.carpstrategy.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreferencesTest {
    @Test
    fun `apariencia se guarda y se recupera, con valores por defecto si esta corrupta`() {
        val custom = AppearanceSettings(AppStyle.APPLE, AppThemeMode.DARK, dynamicColor = true)
        assertEquals(custom, AppearanceSettings.decode(custom.encode()))
        assertEquals(AppearanceSettings(), AppearanceSettings.decode(null))
        assertEquals(AppearanceSettings(), AppearanceSettings.decode("XX;YY;zz"))
    }

    @Test
    fun `ubicacion se guarda y se recupera`() {
        val place = FishingLocation("Valuengo; cola", GeoPoint(38.3, -6.62))
        val decoded = LocationCodec.decode(LocationCodec.encode(place))
        assertEquals("Valuengo, cola", decoded.name)
        assertEquals(38.3, decoded.point.latitude, 1e-6)
        assertEquals(-6.62, decoded.point.longitude, 1e-6)
    }

    @Test
    fun `ubicacion corrupta o ausente vuelve a Brovales`() {
        assertEquals(DefaultLocation.value, LocationCodec.decode(null))
        assertEquals(DefaultLocation.value, LocationCodec.decode("abc;def"))
        assertEquals(DefaultLocation.value, LocationCodec.decode("95;0;x"))
    }
}
