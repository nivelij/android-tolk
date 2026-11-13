package dev.nivelij.tolk.data

import java.util.Locale

data class Language(val code: String, val name: String, val native: String) {
    val isAuto get() = code == AUTO_CODE
}

const val AUTO_CODE = "auto"

object Languages {
    val auto = Language(AUTO_CODE, "Auto-detect", "")

    private val variants = listOf(
        Language("zh-Hans", "Chinese (Simplified)", "简体中文"),
        Language("zh-Hant", "Chinese (Traditional)", "繁體中文"),
        Language("pt-BR", "Portuguese (Brazil)", "Português (Brasil)"),
        Language("sr-Latn", "Serbian (Latin)", "Srpski (latinica)"),
    )

    val all: List<Language> by lazy {
        val base = Locale.getISOLanguages().mapNotNull { code ->
            if (code == "zh") return@mapNotNull null
            val locale = Locale.forLanguageTag(code)
            val name = locale.getDisplayLanguage(Locale.ENGLISH)
            if (name.isBlank() || name.equals(code, ignoreCase = true)) return@mapNotNull null
            val native = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
            Language(code, name, native)
        }
        (base + variants).distinctBy { it.name }.sortedBy { it.name }
    }

    private val aliases = mapOf(
        "zh" to "zh-Hans", "zh-cn" to "zh-Hans", "zh-sg" to "zh-Hans",
        "zh-tw" to "zh-Hant", "zh-hk" to "zh-Hant", "zh-mo" to "zh-Hant",
        "pt-pt" to "pt", "in" to "id", "iw" to "he", "ji" to "yi",
    )

    fun byCode(raw: String): Language {
        val code = aliases[raw.lowercase()] ?: raw
        return if (code == AUTO_CODE) auto
        else all.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: all.firstOrNull { it.code.equals(code.substringBefore('-'), ignoreCase = true) }
            ?: Language(code, code, code)
    }

    fun search(query: String): List<Language> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.native.contains(q, ignoreCase = true) ||
                it.code.equals(q, ignoreCase = true)
        }
    }
}
