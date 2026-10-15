package com.uiptv.mobile.shared.epg

/** Literal database rules; exact identities always take precedence over these name fallbacks. */
class MobileEpgNameRules(rules: List<Rule>) {
    data class Rule(val term: String, val scope: String)
    private val ordered = rules.distinct().sortedWith(compareBy<Rule> { it.term }.thenBy { it.scope })
    val canonical = ordered.joinToString("\n") { "${it.scope}:${it.term.length}:${it.term}" }
    private fun alternatives(scope: String) = ordered.filter { it.scope == scope }.map { it.term }
        .sortedWith(compareByDescending<String> { it.length }.thenBy { it }).joinToString("|") { Regex.escape(it) }.ifEmpty { "(?!)" }
    private val annotations = alternatives("annotation").let { Regex("\\(\\s*(?:$it)\\s*\\)|\\[\\s*(?:$it)\\s*\\]", RegexOption.IGNORE_CASE) }
    private val qualities = Regex("(?<![\\p{L}\\p{N}])(?:${alternatives("quality")})(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    fun display(value: String) = MobileXmltvGuide.normalize(annotations.replace(MobileXmltvGuide.normalize(value), " "))
    fun quality(value: String) = if (value.trim().startsWith("###")) "" else qualities.replace(display(value), " ")
        .replace(Regex("[Ⓐ-Ⓩⓐ-ⓩ]"), "").replace(Regex("[^\\p{L}\\p{N}+]"), "")
    fun aliases(names: Map<String,String>, quality: Boolean): Map<String,String> {
        val result=mutableMapOf<String,String>(); val ambiguous=mutableSetOf<String>()
        names.forEach { (name,id) ->
            val key=if (quality) quality(name) else display(name)
            val previous=result.put(key,id)
            if (previous != null && previous != id) ambiguous += key
        }
        ambiguous.forEach(result::remove); result.remove(""); return result.toMap()
    }
}
