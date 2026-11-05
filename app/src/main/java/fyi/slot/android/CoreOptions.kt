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
        val up = s.upscale.coerceIn(1, 4)
        when (coreId) {
            "mgba" -> {
                v += Variable("mgba_color_correction", if (s.colorCorrection) "Auto" else "OFF")
                v += Variable("mgba_skip_bios", "ON")
                if (gb) mgbaPalette(s.gbPalette)?.let { v += Variable("mgba_gb_colors", it) }
            }
            "flycast" -> {
                // this build still uses the old reicast_ option names; threaded rendering needs a shared GL context
                // LibretroDroid does not provide, and leaves the screen black
                v += Variable("reicast_threaded_rendering", "disabled")
                v += Variable("reicast_delay_frame_swapping", "disabled")
                v += Variable("reicast_internal_resolution", "${640 * up}x${480 * up}")
            }
            // Settings > Screen > Upscaling: the 3D cores render at 1x (native) up to 4x
            "ppsspp" -> v += Variable("ppsspp_internal_resolution", listOf("480x272", "960x544", "1440x816", "1920x1088")[up - 1])
            "mupen64plus_next_gles3" -> v += Variable("mupen64plus-43screensize", "${640 * up}x${480 * up}")
            "pcsx_rearmed" -> v += Variable("pcsx_rearmed_neon_enhancement_enable", if (up >= 2) "enabled" else "disabled")
            "swanstation" -> {
                v += Variable("swanstation_GPU_Renderer", "OpenGL")
                v += Variable("swanstation_GPU_ResolutionScale", up.toString())
                v += Variable("swanstation_GPU_PGXPEnable", if (s.pgxp) "true" else "false")
                v += Variable("swanstation_GPU_PGXPCulling", "true")
                v += Variable("swanstation_GPU_PGXPTextureCorrection", "true")
            }
            "melondsds" -> {
                // a DS without any BIOS files (melonDS's own), straight into the game, both screens stacked
                v += Variable("melonds_console_mode", "ds")
                v += Variable("melonds_sysfile_mode", "builtin")
                v += Variable("melonds_boot_mode", "direct")
                v += Variable("melonds_render_mode", "software")
                v += Variable("melonds_threaded_renderer", "enabled")
                v += Variable("melonds_jit_enable", "enabled")
                v += Variable("melonds_number_of_screen_layouts", "1")
                v += Variable("melonds_screen_layout1", "top-bottom")
                v += Variable("melonds_screen_gap", "0")
                v += Variable("melonds_show_cursor", "touching")
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
