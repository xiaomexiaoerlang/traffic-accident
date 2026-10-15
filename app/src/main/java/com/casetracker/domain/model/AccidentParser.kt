package com.casetracker.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * 从自然语言事故描述中解析结构化信息（智能录入）。
 * 支持：日期、时间、地点、当事人、办案民警、是否逃逸。
 */
object AccidentParser {

    data class ParsedResult(
        val accidentDate: Long = 0L,
        val accidentTime: String = "",
        val location: String = "",
        val parties: List<String> = emptyList(),
        val handlingOfficer: String = "",
        val hitAndRun: Boolean = false,
        val title: String = "",
        val description: String = ""
    )

    private val COMMON_SURNAMES =
        "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳酆鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄和穆萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝董梁杜阮蓝闵席季麻强贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍虞万支柯昝管卢莫经房裘缪干解应宗丁宣贲邓郁单杭洪包诸左石崔吉钮龚程嵇邢滑裴陆荣翁荀羊於惠甄曲家封芮羿储靳汲邴糜松井段富巫乌焦巴弓牧隗山谷车侯宓蓬全郗班仰秋仲伊宫宁仇栾暴甘斜厉戎祖武符刘景詹束龙叶幸司韶郜黎蓟薄印宿白怀蒲邰从鄂索咸籍赖卓蔺屠蒙池乔阴郁胥能苍双闻莘党翟谭贡劳逄姬申扶堵冉宰郦雍卻璩桑桂濮牛寿通边扈燕冀浦尚农温别庄晏柴瞿阎充慕连茹习宦艾鱼容向古易慎戈廖庾终暨居衡步都耿满弘匡国文寇广禄阙东欧殳沃利蔚越夔隆师巩厍聂晁勾敖融冷訾辛阚那简饶空曾毋沙乜养鞠须丰巢关蒯相查后荆红游竺权逯盖益桓公"

    /** 非人名词（动词/名词），命中则过滤掉 */
    private val STOP_WORDS = setOf(
        "驾驶", "开车", "驾车", "骑车", "发生", "碰撞", "相撞", "追尾", "受伤", "事故",
        "造成", "导致", "行驶", "正常", "小型", "汽车", "摩托", "电动车", "自行车",
        "路边", "路口", "转弯", "直行", "掉头", "停靠", "逃逸", "现场", "民警", "交警",
        "对方", "我方", "双方", "伤者", "死者", "司机", "车主", "车上"
    )

    /** 解析完整描述文本 */
    fun parse(text: String): ParsedResult {
        val trimmed = text.trim()
        val date = parseDate(trimmed)
        val time = parseTime(trimmed)
        val location = parseLocation(trimmed)
        val officer = parseOfficer(trimmed)
        val hitRun = trimmed.contains("逃逸") || trimmed.contains("跑了") || trimmed.contains("离开现场")
        val parties = parseParties(trimmed)

        return ParsedResult(
            accidentDate = date,
            accidentTime = time,
            location = location,
            parties = parties,
            handlingOfficer = officer,
            hitAndRun = hitRun,
            description = trimmed
        )
    }

    /** 解析日期（支持 2026年9月15日 / 2026-09-15 / 2026/09/15） */
    fun parseDate(text: String): Long {
        val patterns = listOf(
            Regex("""(\d{4})\s*[年./-]\s*(\d{1,2})\s*[月./-]\s*(\d{1,2})\s*[日号]?""")
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val (y, mo, d) = m.destructured
            try {
                val cal = java.util.Calendar.getInstance()
                cal.clear()
                cal.set(y.toInt(), mo.toInt() - 1, d.toInt(), 0, 0, 0)
                return cal.timeInMillis
            } catch (_: Exception) {}
        }
        return 0L
    }

    /** 解析时间："8点30分" / "5时10分" / "08:30" → 规范为 "8点30分许" */
    fun parseTime(text: String): String {
        // 8点30分 / 5时10分
        Regex("""(\d{1,2})\s*[点时]\s*(\d{1,2})\s*分?许?""").find(text)?.let { m ->
            return "${m.groupValues[1].toInt()}点${m.groupValues[2].toInt()}分许"
        }
        // 08:30
        Regex("""(\d{1,2})\s*[:：]\s*(\d{2})\s*分?""").find(text)?.let { m ->
            return "${m.groupValues[1].toInt()}点${m.groupValues[2].toInt()}分许"
        }
        return ""
    }

    /** 解析地点：在/于 … 发生/相撞/碰撞/追尾/事故/造成/与 */
    fun parseLocation(text: String): String {
        val m = Regex("""(?:在|于|事发于|发生于)\s*([\s\S]{2,30}?)(?:发生|相撞|碰撞|追尾|事故|造成|导致|与)""").find(text)
        return m?.groupValues?.get(1)?.trim()?.trim('，', ',', '。', '的') ?: ""
    }

    /** 解析办案民警：办案民警/主办民警/民警/交警 + 2-3 个汉字 */
    fun parseOfficer(text: String): String {
        val patterns = listOf(
            Regex("""(?:办案民警|主办民警|经办民警)\s*[:：]?\s*([\u4e00-\u9fa5]{2,3})"""),
            Regex("""(?:民警|交警)\s*([\u4e00-\u9fa5]{2,3})""")
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            var name = m.groupValues[1]
            if (name.length == 3 && name[2] in "到出来去处赶场理") name = name.substring(0, 2)
            if (name.length >= 2) return name
        }
        return ""
    }

    /** 解析当事人：常见姓氏 + 1-2 汉字，后跟驾驶/发生/碰撞/与 等动词边界 */
    fun parseParties(text: String): List<String> {
        val names = mutableListOf<String>()

        // 模式A：与/和 + 人名（李四发生碰撞、与张三相撞）
        Regex("""[与和]([\u4e00-\u9fa5]{2,3})(?=[发生碰撞相撞追尾受伤肇])""").findAll(text).forEach { m ->
            val name = m.groupValues[1]
            if (!STOP_WORDS.contains(name)) names.add(name)
        }

        // 模式B：句首/标点后 姓氏开头人名（张三驾驶、李四开车）
        val surnamePattern = Pattern.compile(
            "(?:^|[，,。：:、\\s])([$COMMON_SURNAMES][\\u4e00-\\u9fa5]{1,2})(?=[驾驶开车骑车骑发生与和相撞碰撞追尾])"
        )
        val matcher = surnamePattern.matcher(text)
        while (matcher.find()) {
            var name = matcher.group(1)
            // 人名可能多吃一个动词字（如"张三驾"），截到 2-3 字且不含动词
            for (cut in minOf(3, name.length) downTo 2) {
                if (!STOP_WORDS.contains(name.substring(0, cut))) {
                    name = name.substring(0, cut)
                    break
                }
            }
            if (name.length in 2..3 && !STOP_WORDS.contains(name)) {
                names.add(name)
            }
        }

        return names.distinct().take(6)
    }
}
