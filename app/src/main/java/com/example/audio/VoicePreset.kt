package com.example.audio

enum class VoicePreset(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val pitchSemitones: Float,
    val formantTone: Float, // -1.0 (deep warm) .. +1.0 (bright airy)
    val echoMix: Float,     // 0.0 .. 0.6
    val robotMix: Float,    // 0.0 .. 1.0
    val hornFilter: Boolean
) {
    NATURAL(
        id = "NATURAL",
        displayName = "Natural",
        subtitle = "Clean studio mic",
        pitchSemitones = 0f,
        formantTone = 0f,
        echoMix = 0f,
        robotMix = 0f,
        hornFilter = false
    ),
    FEMALE(
        id = "FEMALE",
        displayName = "Female Voice",
        subtitle = "Higher pitch & airy tone",
        pitchSemitones = 4.8f,
        formantTone = 0.65f,
        echoMix = 0.04f,
        robotMix = 0f,
        hornFilter = false
    ),
    MALE(
        id = "MALE",
        displayName = "Male Voice",
        subtitle = "Lower pitch & chest warmth",
        pitchSemitones = -4.5f,
        formantTone = -0.60f,
        echoMix = 0.04f,
        robotMix = 0f,
        hornFilter = false
    ),
    KID(
        id = "KID",
        displayName = "Young / Kid",
        subtitle = "Playful bright high voice",
        pitchSemitones = 7.5f,
        formantTone = 0.80f,
        echoMix = 0f,
        robotMix = 0f,
        hornFilter = false
    ),
    DEEP_GIANT(
        id = "DEEP_GIANT",
        displayName = "Deep Bass",
        subtitle = "Heavy announcer resonance",
        pitchSemitones = -7.5f,
        formantTone = -0.85f,
        echoMix = 0.14f,
        robotMix = 0f,
        hornFilter = false
    ),
    MEGAPHONE(
        id = "MEGAPHONE",
        displayName = "PA Bullhorn",
        subtitle = "Crisp outdoor megaphone",
        pitchSemitones = 0.5f,
        formantTone = 0.35f,
        echoMix = 0.10f,
        robotMix = 0f,
        hornFilter = true
    ),
    ROBOT(
        id = "ROBOT",
        displayName = "Cyber Robot",
        subtitle = "Metallic synth modulation",
        pitchSemitones = -1.5f,
        formantTone = 0.20f,
        echoMix = 0.12f,
        robotMix = 0.78f,
        hornFilter = true
    ),
    CUSTOM(
        id = "CUSTOM",
        displayName = "Custom Studio",
        subtitle = "Dial pitch, tone & echo",
        pitchSemitones = 0f,
        formantTone = 0f,
        echoMix = 0f,
        robotMix = 0f,
        hornFilter = false
    );

    companion object {
        fun fromId(id: String): VoicePreset = entries.firstOrNull { it.id == id } ?: NATURAL
    }
}
