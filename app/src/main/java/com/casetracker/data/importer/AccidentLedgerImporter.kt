package com.casetracker.data.importer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * v1.85 事故台账（.xlsx）解析器：零第三方依赖，直接解压 zip 读取
 * xl/sharedStrings.xml（共享字符串）+ xl/worksheets/sheet1.xml（首个工作表）。
 * 表头（第 2 行）固定为：
 *   事故时间 | 当事人 | 地点 | 受案 | 快报 | 鉴定 | 材料上传 | 送达 | 物品返还 | 调查报告 | 事故认定书 | 送达 | 违法行为处罚 | 责任民警
 * 列索引（0 起）：0 事故时间 / 1 当事人 / 2 地点 / 3 受案 / 4 快报 / 5 鉴定 / 6 材料上传 / 7 送达(鉴定) / 8 物品返还 /
 *   9 调查报告 / 10 事故认定书 / 11 送达(认定书) / 12 违法行为处罚 / 13 责任民警
 */
data class LedgerRow(
    val accidentTime: String,
    val parties: String,
    val location: String,
    val officer: String,
    /** 流程列（索引 3..12）原始文本 */
    val cols: Map<Int, String>
)

object AccidentLedgerImporter {

    fun parse(stream: InputStream): List<LedgerRow> {
        val shared = ArrayList<String>()
        var sheetXml: String? = null
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" -> {
                        val bytes = zip.readBytes()
                        shared.clear()
                        shared.addAll(parseSharedStrings(ByteArrayInputStream(bytes)))
                    }
                    entry.name.startsWith("xl/worksheets/sheet") && sheetXml == null -> {
                        sheetXml = zip.readBytes().toString(Charsets.UTF_8)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return parseSheet(sheetXml ?: return emptyList(), shared)
    }

    /** 共享字符串表：<sst><si><t>文本</t></si>...</sst> */
    private fun parseSharedStrings(input: InputStream): List<String> {
        val out = ArrayList<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "t") {
                out.add(parser.nextText())
            }
            event = parser.next()
        }
        return out
    }

    /** 工作表：<row r="3"><c r="A3" t="s"><v>0</v></c>...</row> */
    private fun parseSheet(xml: String, shared: List<String>): List<LedgerRow> {
        val rows = ArrayList<LedgerRow>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")

        var rowCells: MutableMap<Int, String>? = null
        var rowIndex = 0
        var cellRef = ""
        var cellType = ""
        var inCell = false
        var inV = false
        var inInlineT = false
        var vText = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        rowCells = HashMap()
                        rowIndex = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowIndex + 1)
                    }
                    "c" -> {
                        inCell = true
                        cellRef = parser.getAttributeValue(null, "r") ?: ""
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        vText = StringBuilder()
                    }
                    "v" -> inV = true
                    "is" -> inInlineT = true
                    "t" -> if (inInlineT) { /* 内联字符串文本由 nextText 处理 */ }
                    else -> {}
                }
                XmlPullParser.TEXT -> {
                    if (inCell && (inV || inInlineT)) vText.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "is" -> { inV = false; inInlineT = false }
                    "c" -> {
                        if (inCell) {
                            val raw = vText.toString().trim()
                            val value = when (cellType) {
                                "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                                else -> raw
                            }
                            val col = colIndexOf(cellRef)
                            if (col >= 0) rowCells?.put(col, value)
                            inCell = false
                        }
                    }
                    "row" -> {
                        rowCells?.let { cells ->
                            if (rowIndex >= 3) {
                                val time = cells[0] ?: ""
                                if (time.isNotBlank()) {
                                    rows.add(
                                        LedgerRow(
                                            accidentTime = time,
                                            parties = cells[1] ?: "",
                                            location = cells[2] ?: "",
                                            officer = cells[13] ?: "",
                                            cols = cells.filterKeys { it in 3..12 }
                                        )
                                    )
                                }
                            }
                        }
                        rowCells = null
                    }
                    else -> {}
                }
                else -> {}
            }
            event = parser.next()
        }
        return rows
    }

    /** "A3" → 0，"B3" → 1，非法返回 -1 */
    private fun colIndexOf(ref: String): Int {
        if (ref.isBlank()) return -1
        var col = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') col = col * 26 + (ch - 'A' + 1)
            else break
        }
        return col - 1
    }
}
