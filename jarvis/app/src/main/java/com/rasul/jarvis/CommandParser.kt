package com.rasul.jarvis

enum class Target { YANDEX, LANE }

sealed class Command {
    object WakeOnly : Command()
    object VolumeUp : Command()
    object VolumeDown : Command()
    object Pause : Command()
    object Resume : Command()
    object Next : Command()
    object Previous : Command()
    data class Play(val target: Target, val query: String?) : Command()
}

/**
 * Разбирает фразу вида «Джарвис, включи яндекс музыку numb».
 * Распознавалка может исказить слово «Джарвис», поэтому есть несколько вариантов.
 */
object CommandParser {

    private val wakeWords = listOf("джарвис", "жарвис", "дарвис", "джервис", "jarvis", "charvis")

    private val playVerbs = "(?:включи|включить|поставь|запусти|сыграй|врубай|найди)"

    // «включи [мне] [в|на] яндекс музыку / лейн / lane ... [трек]»
    private val targetRegex = Regex(
        """^$playVerbs\s+(?:мне\s+)?(?:в\s+|на\s+)?(яндекс\s*музык\w*|яндекс|лейн\w*|лэйн\w*|lane)\s*(.*)$"""
    )

    // «включи <что-то>» без указания приложения
    private val anyPlayRegex = Regex("""^$playVerbs\s+(?:мне\s+)?(.+)$""")

    private val fillerRegex = Regex("""^(?:трек|песню|песня|композицию|музыку|музыка)\s*""")

    private val volumeUp = Regex("громче|погромче|прибавь|увеличь громкость|звук больше")
    private val volumeDown = Regex("тише|потише|убавь|уменьши|звук меньше")
    private val pause = Regex("^(стоп|пауза|останови|остановись|выключи музыку|хватит|замолчи|тихо)")
    private val resume = Regex("^(продолжи|продолжай|играй|воспроизведи|возобнови|плей)$")
    private val next = Regex("следующ|дальше|пропусти|переключи")
    private val previous = Regex("предыдущ|назад|прошл")

    /**
     * @param requireWake если true — без слова «Джарвис» вернётся null.
     *                    Если false (мы уже ждём команду после «Джарвис») — разбираем как есть.
     */
    fun parse(raw: String, defaultTarget: Target = Target.YANDEX, requireWake: Boolean = true): Command? {
        val text = raw.lowercase().replace('ё', 'е').trim()

        val hit = wakeWords
            .map { it to text.indexOf(it) }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }

        val rest = if (hit != null) {
            text.substring(hit.second + hit.first.length).trim(' ', ',', '.', '!', '?')
        } else {
            if (requireWake) return null
            text
        }

        if (rest.isEmpty()) return if (hit != null) Command.WakeOnly else null
        return parseCommand(rest, defaultTarget)
    }

    private fun parseCommand(rest: String, defaultTarget: Target): Command? {
        targetRegex.find(rest)?.let { m ->
            val name = m.groupValues[1]
            val target = if (name.startsWith("яндекс")) Target.YANDEX else Target.LANE
            return Command.Play(target, cleanQuery(m.groupValues[2]))
        }

        anyPlayRegex.find(rest)?.let { m ->
            return Command.Play(defaultTarget, cleanQuery(m.groupValues[1]))
        }

        return when {
            volumeUp.containsMatchIn(rest) -> Command.VolumeUp
            volumeDown.containsMatchIn(rest) -> Command.VolumeDown
            pause.containsMatchIn(rest) -> Command.Pause
            resume.containsMatchIn(rest) -> Command.Resume
            next.containsMatchIn(rest) -> Command.Next
            previous.containsMatchIn(rest) -> Command.Previous
            else -> null
        }
    }

    private fun cleanQuery(q: String): String? {
        val cleaned = q.trim().replace(fillerRegex, "").trim()
        return cleaned.ifEmpty { null }
    }
}
