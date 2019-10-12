package cn.yiiguxing.plugin.translate.trans.text

import cn.yiiguxing.plugin.translate.trans.GoogleTranslation
import cn.yiiguxing.plugin.translate.ui.StyledViewer
import cn.yiiguxing.plugin.translate.util.text.appendString
import cn.yiiguxing.plugin.translate.util.text.getStyleOrAdd
import cn.yiiguxing.plugin.translate.util.text.insert
import cn.yiiguxing.plugin.translate.util.text.rangeSize
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.text.*

/**
 * GoogleDictDocument
 *
 * Created by Yii.Guxing on 2019/10/12.
 */
class GoogleDictDocument private constructor(val dictionaries: List<Dict>) : TranslationDocument {

    override val translations: Set<String> = dictionaries.map { it.terms }.flatten().toSet()

    override fun setupTo(viewer: StyledViewer) {
        viewer.styledDocument.apply {
            initStyle()
            insertDictionaries(dictionaries)
        }
    }

    enum class EntryType { WORD, REVERSE_TRANSLATION }

    /**
     * Dictionary
     */
    data class Dict(
        val partOfSpeech: String,
        val terms: List<String> = emptyList(),
        val entries: List<DictEntry> = emptyList()
    )

    /**
     * Entry of Dictionary
     */
    data class DictEntry(val word: String, val reverseTranslation: List<String> = emptyList())

    object Parser : TranslationDocument.Parser<GoogleTranslation, GoogleDictDocument> {
        override fun parse(input: GoogleTranslation): GoogleDictDocument {
            val dictionaries = input.dict?.map { gDict ->
                val entries = gDict.entry.map { DictEntry(it.word, it.reverseTranslation ?: emptyList()) }
                Dict(gDict.pos, gDict.terms, entries)
            } ?: emptyList()

            return GoogleDictDocument(dictionaries)
        }
    }

    companion object {
        private const val POS_PARAGRAPH_STYLE_FIRST = "g_dict_part_of_speech_paragraph_first"
        private const val POS_PARAGRAPH_STYLE = "g_dict_part_of_speech_paragraph"
        private const val ENTRY_PARAGRAPH_STYLE = "g_dict_entry_paragraph"
        private const val ENTRY_END_PARAGRAPH_STYLE = "g_dict_entry_end_paragraph"
        private const val POS_STYLE = "g_dict_part_of_speech"
        private const val WORD_STYLE = "g_dict_word"
        private const val REVERSE_TRANSLATION_STYLE = "g_dict_reverse_translation"
        private const val SEPARATOR_STYLE = "g_dict_separator"
        private const val FOLDING_STYLE = "g_dict_folding"

        private fun StyledDocument.initStyle() {
            val defaultStyle = getStyle(StyleContext.DEFAULT_STYLE)
            initPosStyle(defaultStyle)
            initEntryStyle(defaultStyle)
            initWordStyle(defaultStyle)
            initReverseTranslationStyle(defaultStyle)
            initFoldingStyle(defaultStyle)
        }

        private fun StyledDocument.initPosStyle(defaultStyle: Style?) {
            getStyleOrAdd(POS_STYLE, defaultStyle) { style ->
                StyleConstants.setForeground(style, JBColor(0x293B2B, 0xDF7CFF))
                StyleConstants.setItalic(style, true)
            }
            getStyleOrAdd(POS_PARAGRAPH_STYLE_FIRST, defaultStyle) { style ->
                StyleConstants.setSpaceBelow(style, JBUI.scale(2f))
            }
            getStyleOrAdd(POS_PARAGRAPH_STYLE, defaultStyle) { style ->
                StyleConstants.setSpaceAbove(style, JBUI.scale(10f))
                StyleConstants.setSpaceBelow(style, JBUI.scale(3f))
            }
        }

        private fun StyledDocument.initEntryStyle(defaultStyle: Style?) {
            val entryParagraphStyle = getStyleOrAdd(ENTRY_PARAGRAPH_STYLE, defaultStyle) { style ->
                StyleConstants.setLeftIndent(style, JBUI.scale(12f))
                StyleConstants.setSpaceBelow(style, JBUI.scale(8f))
            }
            getStyleOrAdd(ENTRY_END_PARAGRAPH_STYLE, entryParagraphStyle) { style ->
                StyleConstants.setSpaceBelow(style, 0f)
            }
        }

        private fun StyledDocument.initWordStyle(defaultStyle: Style?) {
            getStyleOrAdd(WORD_STYLE, defaultStyle) { style ->
                val color = JBColor(0x9C27B0, 0xFFFF00)
                StyleConstants.setForeground(style, color)

                val hoverColor = JBColor(0x7400FF, 0xDF9B00)
                val mouseListener = StyledViewer.ColoredMouseListener(color, hoverColor, EntryType.WORD)
                StyledViewer.StyleConstants.setMouseListener(style, mouseListener)
            }
        }

        private fun StyledDocument.initReverseTranslationStyle(defaultStyle: Style?) {
            getStyleOrAdd(REVERSE_TRANSLATION_STYLE, defaultStyle) { style ->
                StyleConstants.setItalic(style, true)
                val color = JBColor(0x3333E8, 0xFFC66D)
                StyleConstants.setForeground(style, color)

                val hoverColor = JBColor(0x762DFF, 0xDF7000)
                val mouseListener = StyledViewer.ColoredMouseListener(color, hoverColor, EntryType.REVERSE_TRANSLATION)
                StyledViewer.StyleConstants.setMouseListener(style, mouseListener)
            }
            getStyleOrAdd(SEPARATOR_STYLE, defaultStyle) { style ->
                StyleConstants.setForeground(style, JBColor(0xFF5555, 0x2196F3))
            }
        }

        private fun StyledDocument.initFoldingStyle(defaultStyle: Style?) {
            getStyleOrAdd(FOLDING_STYLE, defaultStyle) { style ->
                StyleConstants.setForeground(style, JBColor(0x777777, 0x888888))
                val background = JBColor(Color(0, 0, 0, 0x18), Color(0xFF, 0xFF, 0xFF, 0x10))
                StyleConstants.setBackground(style, background)
            }
        }

        private fun StyledDocument.insertDictionaries(dictionaries: List<Dict>) {
            with(dictionaries) {
                if (isEmpty()) {
                    return
                }

                val lastIndex = size - 1
                forEachIndexed { index, dict ->
                    insertDict(dict, index == 0, index == lastIndex)
                }
            }
        }

        private fun StyledDocument.setParagraphStyle(
            offset: Int? = null,
            len: Int = 0,
            style: String,
            replace: Boolean = true
        ) {
            setParagraphAttributes(offset ?: length, len, getStyle(style), replace)
        }

        private fun StyledDocument.insertDict(dict: Dict, isFirst: Boolean, isLast: Boolean) {
            setParagraphStyle(style = if (isFirst) POS_PARAGRAPH_STYLE_FIRST else POS_PARAGRAPH_STYLE)
            appendString(dict.partOfSpeech, POS_STYLE)
            appendString("\n")

            setParagraphStyle(style = if (isLast) ENTRY_END_PARAGRAPH_STYLE else ENTRY_PARAGRAPH_STYLE)
            val hasWordOnly = dict.entries
                .asSequence()
                .filter { it.reverseTranslation.isEmpty() }
                .map { it.word }
                .toList()
                .run {
                    forEachIndexed { index, word ->
                        if (index > 0) {
                            appendString(", ", SEPARATOR_STYLE)
                        }
                        appendString(word, WORD_STYLE)
                    }

                    isNotEmpty()
                }

            dict.entries
                .filter { it.reverseTranslation.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
                ?.let { entries ->
                    if (hasWordOnly) {
                        appendString("\n")
                    }

                    val displayCount = if (hasWordOnly) 2 else 3
                    val lastIndex = entries.size - 1
                    entries.take(displayCount).forEachIndexed { index, dictEntry ->
                        if (index > 0) {
                            appendString("\n")
                        }
                        insertDictEntry(length, dictEntry, index == lastIndex)
                    }

                    if (entries.size > displayCount) {
                        appendString("\n")

                        val foldingEntries = entries.takeLast(entries.size - displayCount)
                        appendFolding(foldingEntries)
                    }
                }

            if (!isLast) {
                appendString("\n")
            }
        }

        private fun StyledDocument.appendFolding(foldingEntries: List<DictEntry>) {
            val placeholder = foldingEntries.run {
                take(3).joinToString(prefix = " ", postfix = if (size > 3) ", ... " else " ") { it.word }
            }

            setParagraphStyle(style = ENTRY_END_PARAGRAPH_STYLE)
            val foldingAttr = SimpleAttributeSet(getStyle(FOLDING_STYLE))
            StyledViewer.StyleConstants.setMouseListener(foldingAttr, createFoldingMouseListener(foldingEntries))
            appendString(placeholder, foldingAttr)
        }

        private fun createFoldingMouseListener(foldingEntries: List<DictEntry>): StyledViewer.FoldingMouseListener {
            return StyledViewer.FoldingMouseListener(foldingEntries) { viewer, element, _ ->
                viewer.styledDocument.apply {
                    var startOffset = element.startOffset
                    remove(startOffset, element.rangeSize)
                    val last = foldingEntries.size - 1
                    foldingEntries.forEachIndexed { index, entry ->
                        if (index > 0) {
                            startOffset = insert(startOffset, "\n")
                        }
                        startOffset = insertDictEntry(startOffset, entry, index == last)
                    }
                }
            }
        }

        private fun StyledDocument.insertDictEntry(offset: Int, dictEntry: DictEntry, isLast: Boolean): Int {
            var currentOffset = insert(offset, dictEntry.word, WORD_STYLE)
            currentOffset = insert(currentOffset, "\n")
            setParagraphStyle(offset, currentOffset - offset, ENTRY_END_PARAGRAPH_STYLE, true)

            val paragraphStart = currentOffset
            dictEntry.reverseTranslation.forEachIndexed { index, reverseTrans ->
                if (index > 0) {
                    currentOffset = insert(currentOffset, ", ", SEPARATOR_STYLE)
                }
                currentOffset = insert(currentOffset, reverseTrans, REVERSE_TRANSLATION_STYLE)
            }

            val style = if (isLast) ENTRY_END_PARAGRAPH_STYLE else ENTRY_PARAGRAPH_STYLE
            setParagraphStyle(paragraphStart, currentOffset - paragraphStart, style, true)

            return currentOffset
        }
    }
}