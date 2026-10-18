package com.wledcontroller.app.sim

/**
 * The effects and palettes a simulated controller offers. Names and numbers follow WLED, so
 * everything that looks effects up by name (holiday themes, light-type suggestions, groups)
 * behaves as it does with real controllers. 2D and audio-reactive effects aren't simulated.
 */
internal object SimCatalog {

    /** WLED effect names by id; "RSVD" slots are hidden by the app. */
    val effects = listOf(
        "Solid", "Blink", "Breathe", "Wipe", "Wipe Random", "Random Colors", "Sweep", "Dynamic", "Colorloop", "Rainbow",
        "Scan", "Scan Dual", "Fade", "Theater", "Theater Rainbow", "Running", "Saw", "Twinkle", "Dissolve", "Dissolve Rnd",
        "Sparkle", "Sparkle Dark", "Sparkle+", "Strobe", "Strobe Rainbow", "Strobe Mega", "Blink Rainbow", "Android", "Chase", "Chase Random",
        "Chase Rainbow", "Chase Flash", "Chase Flash Rnd", "Rainbow Runner", "Colorful", "Traffic Light", "Sweep Random", "Chase 2", "Aurora", "Stream",
        "Scanner", "Lighthouse", "Fireworks", "Rain", "Tetrix", "Fire Flicker", "Gradient", "Loading", "Rolling Balls", "Fairy",
        "Two Dots", "Fairytwinkle", "Running Dual", "RSVD", "Chase 3", "Tri Wipe", "Tri Fade", "Lightning", "ICU", "Multi Comet",
        "Scanner Dual", "Stream 2", "Oscillate", "Pride 2015", "Juggle", "Palette", "Fire 2012", "Colorwaves", "Bpm", "Fill Noise",
        "Noise 1", "Noise 2", "Noise 3", "Noise 4", "Colortwinkles", "Lake", "Meteor", "Meteor Smooth", "Railway", "Ripple",
        "Twinklefox", "Twinklecat", "Halloween Eyes", "Solid Pattern", "Solid Pattern Tri", "Spots", "Spots Fade", "Glitter", "Candle", "Fireworks Starburst",
        "Fireworks 1D", "Bouncing Balls", "Sinelon", "Sinelon Dual", "Sinelon Rainbow", "Popcorn", "Drip", "Plasma", "Percent", "Ripple Rainbow",
        "Heartbeat", "Pacifica", "Candle Multi", "Solid Glitter", "Sunrise", "Phased", "Twinkleup", "Noise Pal", "Sine", "Phased Noise",
        "Flow", "Chunchun", "Dancing Shadows", "Washing Machine", "RSVD", "Blends", "TV Simulator", "Dynamic Smooth",
    )

    /** WLED's fxdata strings ("sliders;colours;palette;flags"): which controls each effect uses. */
    fun fxData(id: Int): String = when (effects.getOrNull(id)) {
        null, "RSVD" -> ""
        "Solid" -> ""
        "Blink", "Strobe", "Strobe Mega", "Blink Rainbow", "Strobe Rainbow" -> "!,Duty cycle;!,!;!;01"
        "Breathe", "Fade" -> "!;!,!;!;01"
        "Rainbow", "Colorloop" -> "!,Saturation;;!;01"
        "Theater", "Theater Rainbow", "Running", "Saw", "Running Dual", "Chase 3", "Railway", "Solid Pattern", "Solid Pattern Tri" -> "!,Width;!,!,!;!;01"
        "Twinkle", "Twinklefox", "Twinklecat", "Colortwinkles", "Twinkleup", "Fairytwinkle", "Fairy" -> "!,Density;!,!;!;01"
        "Sparkle", "Sparkle Dark", "Sparkle+", "Glitter", "Solid Glitter" -> "!,!;Bg,Fx;!;01"
        "Fireworks", "Fireworks Starburst", "Fireworks 1D", "Popcorn" -> "!,Frequency;!,!;!;01"
        "Fire 2012", "Fire Flicker", "Candle", "Candle Multi" -> "Spark rate,Diffusion;;!;01"
        "Meteor", "Meteor Smooth", "Multi Comet", "Scanner", "Scanner Dual", "Sinelon", "Sinelon Dual", "Sinelon Rainbow" -> "!,Trail;!,!;!;01"
        "Lightning", "Heartbeat" -> "!,!;!,!;!;01"
        "Halloween Eyes" -> "Duration,Eye fade time;!,!;!;01"
        "Percent" -> ",% of fill;!,!;!;01"
        "Sunrise" -> "Time [min],Width;;!;01"
        else -> "!,!;!,!,!;!;01"
    }

    /**
     * Palettes: (name, stops). A stop is (position 0..255, 0xRRGGBB). The first six are WLED's
     * built-ins that follow the effect's own colours, handled by the renderer.
     */
    val palettes: List<Pair<String, List<Pair<Int, Int>>>> = listOf(
        "Default" to emptyList(),
        "* Random Cycle" to emptyList(),
        "* Color 1" to emptyList(),
        "* Colors 1&2" to emptyList(),
        "* Color Gradient" to emptyList(),
        "* Colors Only" to emptyList(),
        "Party" to even(0x5500AB, 0x84007C, 0xB5004B, 0xE5001B, 0xE81700, 0xB84700, 0xAB7700, 0xABAB00, 0xAB5500, 0xDD2200, 0xF2000E, 0xC2003E, 0x8F0071, 0x5F00A1, 0x2F00D0, 0x0007F9),
        "Cloud" to even(0x0000FF, 0x00008B, 0x00008B, 0x00008B, 0x00008B, 0x00008B, 0x00008B, 0x00008B, 0x0000FF, 0x00008B, 0x87CEEB, 0x87CEEB, 0xADD8E6, 0xFFFFFF, 0xADD8E6, 0x87CEEB),
        "Lava" to even(0x000000, 0x800000, 0x000000, 0x800000, 0x8B0000, 0x8B0000, 0x800000, 0x8B0000, 0x8B0000, 0x8B0000, 0xFF0000, 0xFFA500, 0xFFFFFF, 0xFFA500, 0xFF0000, 0x8B0000),
        "Ocean" to even(0x191970, 0x00008B, 0x191970, 0x000080, 0x00008B, 0x0000CD, 0x2E8B57, 0x008080, 0x5F9EA0, 0x0000FF, 0x008B8B, 0x6495ED, 0x7FFFD4, 0x2E8B57, 0x00FFFF, 0x87CEFA),
        "Forest" to even(0x006400, 0x006400, 0x556B2F, 0x006400, 0x008000, 0x228B22, 0x6B8E23, 0x008000, 0x2E8B57, 0x66CDAA, 0x32CD32, 0x9ACD32, 0x90EE90, 0x7CFC00, 0x66CDAA, 0x228B22),
        "Rainbow" to even(0xFF0000, 0xD52A00, 0xAB5500, 0xAB7F00, 0xABAB00, 0x56D500, 0x00FF00, 0x00D52A, 0x00AB55, 0x0056AA, 0x0000FF, 0x2A00D5, 0x5500AB, 0x7F0081, 0xAB0055, 0xD5002B),
        "Rainbow Bands" to even(0xFF0000, 0x000000, 0xAB5500, 0x000000, 0xABAB00, 0x000000, 0x00FF00, 0x000000, 0x00AB55, 0x000000, 0x0000FF, 0x000000, 0x5500AB, 0x000000, 0xAB0055, 0x000000),
        "Sunset" to listOf(0 to 0x780000, 22 to 0xB31600, 51 to 0xFF6800, 85 to 0xA71601, 135 to 0x640067, 198 to 0x10009F, 255 to 0x0000A0),
        "Rivendell" to listOf(0 to 0x18452C, 101 to 0x49692E, 165 to 0x81B246, 242 to 0xC8C995, 255 to 0xC8C995),
        "Breeze" to listOf(0 to 0x105974, 89 to 0x8ED6D1, 153 to 0xF7F2DF, 255 to 0x0A9CA3),
        "Red & Blue" to even(0xFF0000, 0x0000FF, 0xFF0000, 0x0000FF),
        "Yellowout" to listOf(0 to 0xBC8701, 255 to 0x2E0E01),
        "Analogous" to listOf(0 to 0x0300FF, 63 to 0x1700FF, 127 to 0x4300FF, 191 to 0x8E002D, 255 to 0xFF0000),
        "Splash" to listOf(0 to 0x7E0BFF, 127 to 0xC5016A, 175 to 0xD2009B, 255 to 0xFF00FF),
        "Pastel" to listOf(0 to 0x0A3E7B, 36 to 0x3D877C, 87 to 0x6AC3A4, 100 to 0xCDE4A3, 107 to 0xFFFF86, 115 to 0xF5D869, 120 to 0xFFF27F, 128 to 0xFFA759, 180 to 0xFF8263, 255 to 0xF34D82),
        "Sunset 2" to listOf(0 to 0xAF4126, 29 to 0xFF9135, 68 to 0xFFDC79, 68 to 0x2E5592, 97 to 0x7ABFE7, 124 to 0xB1EAEB, 255 to 0xFFFFFF),
        "Beach" to listOf(0 to 0xFFFC2E, 15 to 0xF2E42E, 37 to 0xF5D46E, 63 to 0x6EBDB0, 255 to 0x06A2BF),
        "Vintage" to listOf(0 to 0x040101, 51 to 0x10000C, 76 to 0x612C11, 101 to 0xC4AB4B, 127 to 0x2E1101, 153 to 0x10000C, 229 to 0x040101, 255 to 0x040101),
        "Departure" to listOf(0 to 0x080300, 42 to 0x170700, 63 to 0x4B2610, 84 to 0xA9631A, 106 to 0xD5A992, 116 to 0xFFFFFF, 138 to 0x87FF8A, 148 to 0x16FF18, 170 to 0x00FF00, 191 to 0x008800, 212 to 0x003700, 255 to 0x003700),
        "Landscape" to listOf(0 to 0x000000, 37 to 0x021901, 76 to 0x0F7305, 127 to 0x4FD501, 128 to 0x7ED347, 130 to 0xBCD1F7, 153 to 0x90B6CD, 204 to 0x3B75FA, 255 to 0x0125C0),
        "Beech" to listOf(0 to 0xFFFCD6, 12 to 0xFFFCD6, 22 to 0xFFFCD6, 26 to 0xBEBF73, 28 to 0x8B95E7, 28 to 0x8B95E7, 50 to 0x2D8FBE, 71 to 0x175E9E, 89 to 0x0C47A9, 104 to 0x1F68C1, 255 to 0x063C4F),
        "Sherbet" to listOf(0 to 0xFF2104, 43 to 0xFF4419, 86 to 0xFF0719, 127 to 0xFF5267, 170 to 0xFFFFF2, 209 to 0x2AFF16, 255 to 0x57FF41),
        "Hult" to listOf(0 to 0xF7B0F7, 48 to 0xFF88FF, 89 to 0xDC1DE2, 160 to 0x07526B, 216 to 0x01988A, 255 to 0x01988A),
        "Hult 64" to listOf(0 to 0x01767A, 66 to 0x01767A, 104 to 0x0A9C9A, 130 to 0xA12C1A, 150 to 0x0A9C9A, 201 to 0x01764E, 239 to 0x017676, 255 to 0x017676),
        "Drywet" to listOf(0 to 0x2F1E02, 42 to 0xD5932A, 84 to 0x67DB34, 127 to 0x03DBCF, 170 to 0x0130D6, 212 to 0x0101EF, 255 to 0x01011C),
        "Jul" to listOf(0 to 0xC20101, 94 to 0x1D1201, 132 to 0x398301, 255 to 0xC20101),
        "Grintage" to listOf(0 to 0x020100, 53 to 0x120100, 104 to 0x451D01, 153 to 0xA77E0A, 255 to 0x2E3804),
        "Rewhi" to listOf(0 to 0x71596B, 72 to 0x9C9E8E, 89 to 0xC0D7BC, 107 to 0xDCF0D8, 141 to 0x9E8EC5, 255 to 0x71596B),
        "Tertiary" to listOf(0 to 0x000AFF, 63 to 0x0060FF, 127 to 0x00FF7A, 191 to 0xFFC800, 255 to 0xFF0C00),
        "Fire" to listOf(0 to 0x000000, 46 to 0x120000, 96 to 0x710000, 108 to 0x8E0300, 119 to 0xAF1100, 146 to 0xD52C02, 174 to 0xFF5204, 188 to 0xFF7304, 202 to 0xFF9C04, 218 to 0xFFCB04, 234 to 0xFFFF04, 244 to 0xFFFF47, 255 to 0xFFFFFF),
        "Icefire" to listOf(0 to 0x000000, 59 to 0x002EFF, 119 to 0x00FFFF, 149 to 0xFFFFFF, 180 to 0xFFFF00, 217 to 0xFF0000, 255 to 0x000000),
        "Cyane" to listOf(0 to 0x0A5544, 25 to 0x1D6D12, 60 to 0x3B8A2A, 93 to 0x53630E, 106 to 0x6E4205, 109 to 0x7B3101, 113 to 0x8B2201, 116 to 0xC0A975, 124 to 0xFFFFFF, 168 to 0xA7CAE7, 255 to 0x2EA3F1),
        "Light Pink" to listOf(0 to 0x13021A, 25 to 0x1A0416, 51 to 0x210613, 76 to 0x441E39, 102 to 0x4D3D60, 109 to 0x6A585D, 114 to 0x8D758A, 122 to 0xC8BDBA, 149 to 0x9F8CA3, 183 to 0x8B83A0, 255 to 0x13021A),
        "Autumn" to listOf(0 to 0x1A0101, 51 to 0x430401, 84 to 0x761B0E, 104 to 0x89983A, 112 to 0x71411D, 122 to 0x85951E, 124 to 0x89983A, 135 to 0x71411D, 142 to 0x8B9A2E, 163 to 0x710D01, 204 to 0x370301, 255 to 0x110101),
        "Magenta" to listOf(0 to 0x000000, 42 to 0x000075, 84 to 0x0000FF, 127 to 0x2A00FF, 170 to 0xFF00FF, 212 to 0xFF37FF, 255 to 0xFFFFFF),
        "Magred" to listOf(0 to 0x000000, 63 to 0x2A0007, 127 to 0xFF002D, 191 to 0xFF0007, 255 to 0xFF0000),
        "Yelmag" to listOf(0 to 0x000000, 42 to 0x2A0000, 84 to 0xFF0000, 127 to 0xFF002D, 170 to 0xFF00FF, 212 to 0xFF3731, 255 to 0xFFFF00),
        "Yelblu" to listOf(0 to 0x0000FF, 63 to 0x0037FF, 127 to 0x00FFFF, 191 to 0x2AFF00, 255 to 0xFFFF00),
        "Orange & Teal" to listOf(0 to 0x00965C, 55 to 0x00965C, 200 to 0xFF4800, 255 to 0xFF4800),
        "Tiamat" to listOf(0 to 0x010214, 33 to 0x022339, 100 to 0x0D8D57, 120 to 0x2AFF7E, 140 to 0x0C9AB0, 155 to 0x5B11CC, 171 to 0xC8B8DA, 200 to 0x40BBE2, 255 to 0xFFFFFF),
        "April Night" to listOf(0 to 0x010533, 10 to 0x0105A1, 25 to 0x010533, 40 to 0x01051B, 61 to 0x2D7701, 76 to 0x01051B, 96 to 0xFF3302, 112 to 0x01051B, 127 to 0xFFD301, 143 to 0x01051B, 162 to 0xC41B5D, 178 to 0x01051B, 196 to 0x2D7701, 255 to 0x01051B),
        "Orangery" to listOf(0 to 0xFF5F17, 30 to 0xFF5200, 60 to 0xDF0D08, 90 to 0x903311, 120 to 0xFF6F0F, 150 to 0xDB0E01, 180 to 0xFF6F0F, 210 to 0x8A0D01, 255 to 0xFF6F0F),
        "C9" to listOf(0 to 0xB80400, 60 to 0xB80400, 65 to 0x902C02, 125 to 0x902C02, 130 to 0x046002, 190 to 0x046002, 195 to 0x070758, 255 to 0x070758),
        "Sakura" to listOf(0 to 0xC4130A, 65 to 0xFF452D, 130 to 0xDF2D48, 195 to 0xFF5267, 255 to 0xDF0D11),
        "Aurora" to listOf(0 to 0x01052D, 64 to 0x00C817, 128 to 0x00FF00, 170 to 0x00F32D, 200 to 0x008707, 255 to 0x01052D),
        "Atlantica" to listOf(0 to 0x001C70, 50 to 0x2060FF, 100 to 0x00F332, 150 to 0x0C5F52, 200 to 0x1977CB, 255 to 0x2860C8),
        "C9 2" to listOf(0 to 0x064104, 45 to 0x064104, 46 to 0x2A3C82, 90 to 0x2A3C82, 91 to 0xBB4004, 135 to 0xBB4004, 136 to 0xC48002, 180 to 0xC48002, 181 to 0x8A1A0A, 255 to 0x8A1A0A),
        "C9 New" to listOf(0 to 0xFF0500, 60 to 0xFF0500, 61 to 0xC4390A, 120 to 0xC4390A, 121 to 0x066402, 180 to 0x066402, 181 to 0x0E1B6C, 255 to 0x0E1B6C),
        "Temperature" to listOf(0 to 0x011B69, 14 to 0x011B69, 28 to 0x01276E, 42 to 0x013375, 56 to 0x0346A0, 70 to 0x177FB9, 84 to 0x4FA4C4, 99 to 0x7DC4AB, 113 to 0xA8D0A3, 127 to 0xD4DC5E, 141 to 0xFAD515, 155 to 0xFAB208, 170 to 0xFA8C05, 184 to 0xF37008, 198 to 0xD54406, 226 to 0x8E0E02, 255 to 0x2B0601),
        "Aurora 2" to listOf(0 to 0x11B10D, 64 to 0x79F205, 128 to 0x19AD79, 192 to 0xFA4D7F, 255 to 0xAB65DD),
        "Retro Clown" to listOf(0 to 0xE3650D, 117 to 0xC23D41, 255 to 0x5F33DC),
        "Candy" to listOf(0 to 0xF3F217, 15 to 0xFC6B08, 142 to 0x2B1E8A, 198 to 0x05030E, 255 to 0x101028),
        "Toxy Reaf" to listOf(0 to 0x01DD35, 255 to 0x491EAE),
        "Fairy Reaf" to listOf(0 to 0xDC13BB, 160 to 0x0CE1DB, 219 to 0xCBF2DF, 255 to 0xFFFFFF),
        "Semi Blue" to listOf(0 to 0x000000, 12 to 0x18040A, 53 to 0x37082A, 80 to 0x081F54, 119 to 0x3833B4, 145 to 0x1C3F98, 186 to 0x000000, 233 to 0x000000, 255 to 0x000000),
        "Pink Candy" to listOf(0 to 0xFFFFFF, 45 to 0x0740FF, 112 to 0xF110D9, 140 to 0xFFFFFF, 155 to 0xF110D9, 196 to 0x7417FA, 255 to 0xFFFFFF),
        "Red Reaf" to listOf(0 to 0x24CEFF, 104 to 0x024FEE, 188 to 0xFF0000, 255 to 0x5E0000),
        "Aqua Flash" to listOf(0 to 0x000000, 66 to 0x82F2F5, 96 to 0xFFFF35, 124 to 0xFFFFFF, 153 to 0xFFFF35, 188 to 0x82F2F5, 255 to 0x000000),
        "Yelblu Hot" to listOf(0 to 0x2B1E39, 58 to 0x490082, 122 to 0x570059, 158 to 0xC51C1C, 183 to 0xDA7516, 219 to 0xEFB116, 255 to 0xF6F713),
        "Lite Light" to listOf(0 to 0x000000, 9 to 0x010101, 40 to 0x050506, 66 to 0x050506, 101 to 0x0A010C, 255 to 0x000000),
        "Red Flash" to listOf(0 to 0x000000, 99 to 0xF20C08, 130 to 0xFDE4A3, 155 to 0xF20C08, 255 to 0x000000),
        "Blink Red" to listOf(0 to 0x040707, 43 to 0x170419, 76 to 0xC30C3B, 109 to 0x8C0E6F, 127 to 0xFF0A60, 165 to 0xB915C5, 204 to 0x3B023A, 255 to 0x060204),
        "Red Shift" to listOf(0 to 0x1F0118, 45 to 0x1F0118, 99 to 0x83051B, 132 to 0xFC2D06, 175 to 0xD30701, 201 to 0x2B0101, 255 to 0x020101),
        "Red Tide" to listOf(0 to 0xFB2E00, 28 to 0xFF8B19, 43 to 0xF6E09F, 58 to 0xFF8B19, 84 to 0xFB2E00, 114 to 0xFF8B19, 140 to 0xC4BA43, 168 to 0xFF8B19, 196 to 0xFB2E00, 216 to 0xFF8B19, 255 to 0x790109),
        "Candy2" to listOf(0 to 0x6D6639, 25 to 0x2A3147, 48 to 0x790821, 73 to 0xF1C329, 89 to 0x2A3147, 130 to 0xF1C329, 163 to 0x2A3147, 186 to 0x790821, 211 to 0x6D6639, 255 to 0x2A3147),
    )

    private fun even(vararg c: Int) = c.mapIndexed { i, rgb -> (i * 255 / (c.size - 1)) to rgb }
}
