package fyi.slot.android

import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import fyi.slot.ui.CartShape
import fyi.slot.ui.Game
import fyi.slot.ui.Settings
import fyi.slot.ui.ShaderChoice

/** Translates slot's settings into libretro core options and LibretroDroid shaders. */
object CoreOptions {
    fun shader(game: Game, s: Settings): ShaderConfig {
        val choice = when (game.system.shape) {
            CartShape.GBA -> s.gbaShader
            CartShape.GB, CartShape.GBC -> s.gbShader
            else -> s.otherShader
        }
        return when (choice) {
            ShaderChoice.Default -> ShaderConfig.Sharp
            ShaderChoice.Lcd -> ShaderConfig.LCD
            ShaderChoice.Crt -> ShaderConfig.CRT
            ShaderChoice.Smooth -> ShaderConfig.CUT3()
        }
    }

    fun variables(game: Game, coreId: String, s: Settings): Array<Variable> {
        val v = ArrayList<Variable>()
        val gb = game.system.shape == CartShape.GB || game.system.shape == CartShape.GBC
        when (coreId) {
            "mgba" -> {
                v += Variable("mgba_color_correction", if (s.colorCorrection) "Auto" else "OFF")
                v += Variable("mgba_skip_bios", "ON")
                if (gb) mgbaPalette(s.gbPalette)?.let { v += Variable("mgba_gb_colors", it) }
            }
            "flycast" -> {
                v += Variable("flycast_threaded_rendering", "disabled")
                v += Variable("flycast_internal_resolution", "640x480")
            }
            "gambatte" -> {
                v += Variable("gambatte_gbc_color_correction", if (s.colorCorrection) "accurate" else "disabled")
                val pal = gambattePalette(s.gbPalette)
                if (pal != null) {
                    v += Variable("gambatte_gb_colorization", "internal")
                    v += Variable("gambatte_gb_internal_palette", pal)
                } else {
                    v += Variable("gambatte_gb_colorization", "auto")
                }
            }
        }
        return v.toTypedArray()
    }

    private fun mgbaPalette(p: String) = when (p) {
        "DMG Green" -> "DMG Green"
        "Pocket" -> "GB Pocket"
        "Light" -> "GB Light"
        "Grayscale" -> "Grayscale"
        else -> null
    }

    private fun gambattePalette(p: String) = when (p) {
        "DMG Green" -> "GB - DMG"
        "Pocket" -> "GB - Pocket"
        "Light" -> "GB - Light"
        "Grayscale" -> "GBC - Grayscale"
        else -> null
    }
}
