package com.casetracker.domain.model

import java.util.regex.Pattern

/**
 * 从自然语言事故描述中解析结构化信息（智能录入）。
 * 支持：日期、时间（含仅小时）、地点、当事人、办案民警、是否逃逸、伤情识别。
 */
object AccidentParser {

    data class ParsedResult(
        val accidentDate: Long = 0L,
        val accidentTime: String = "",
        val location: String = "",
        val parties: List<String> = emptyList(),
        val handlingOfficer: String = "",
        val hitAndRun: Boolean = false,
        val isInjury: Boolean = false,
        val injuryDetail: String = "",
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
        "对方", "我方", "双方", "伤者", "死者", "司机", "车主", "车上",
        // 车辆类名词：不作为当事人识别
        "车辆", "受损", "货车", "轿车", "客车", "面包车", "小轿车", "出租车", "公交车",
        "机动车", "非机动车", "拖拉机", "挂车", "三轮车", "摩托车", "单车", "皮卡",
        "越野车", "卡车", "罐车", "渣土车", "水泥车", "救护车", "消防车", "电瓶车",
        // 补充过滤：方位/动作/虚词，避免被误识别为人名
        "相对", "方向", "操作", "不当", "前方", "左侧", "右侧", "同向", "逆向",
        "由东", "由西", "由南", "由北", "向东", "向西", "向南", "向北",
        "途经", "沿线", "旁边", "附近", "位置", "路段", "期间", "之后", "之前",
        "进行", "属于", "由于", "因为", "因此", "随即", "随后", "最终"
    )

    /** v1.74：方位词前缀，与姓名无关，不作为姓名组成部分（识别时先剥离，避免「前方郑露」→「方郑露」） */
    private val DIRECTION_PREFIXES = listOf(
        "前方", "后方", "左侧", "右侧", "左边", "右边", "旁边", "对向", "同向", "逆向", "相对"
    )

    /** v1.81：驾驶状态修饰语（夹在姓名与「驾驶」之间），识别姓名时从尾部剥离，避免「汪霞醉酒后驾驶」提取失败 */
    private val DRINK_MODIFIERS = listOf(
        "醉酒后", "饮酒后", "醉酒", "酒后", "饮酒"
    )

    /** 伤情关键词：命中任一即判定为伤人事故 */
    private val INJURY_KEYWORDS = listOf(
        "受伤", "伤者", "致伤", "伤势", "轻伤", "重伤", "骨折", "昏迷", "流血",
        "抢救", "送医", "住院", "伤亡", "挫伤", "擦伤", "皮外伤", "内伤", "伤残",
        "受伤人员", "人员受伤"
    )

    /** 解析完整描述文本 */
    fun parse(text: String): ParsedResult {
        val trimmed = text.trim()
        val date = parseDate(trimmed)
        val time = parseTime(trimmed)
        val location = parseLocation(trimmed)
        val officer = parseOfficer(trimmed)
        val hitRun = trimmed.contains("逃逸") || trimmed.contains("跑了") || trimmed.contains("离开现场")
        val injury = containsInjury(trimmed)
        val injuryDetail = if (injury) parseInjuryDetail(trimmed) else ""
        val parties = parseParties(trimmed)

        return ParsedResult(
            accidentDate = date,
            accidentTime = time,
            location = location,
            parties = parties,
            handlingOfficer = officer,
            hitAndRun = hitRun,
            isInjury = injury,
            injuryDetail = injuryDetail,
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

    /**
     * 解析时间：
     *  - "22时07分许"/"8点30分" → "22点07分许"/"8点30分许"（分钟按原文保留，如 07）
     *  - "22时许"/"22时"/"22点左右" → "22时许"（无分钟时仅到小时）
     */
    fun parseTime(text: String): String {
        // 有分钟：X时/点Y分（许）
        Regex("""(\d{1,2})\s*[点时]\s*(\d{1,2})\s*分?许?""").find(text)?.let { m ->
            return "${m.groupValues[1].toInt()}点${m.groupValues[2]}分许"
        }
        // 有分钟：HH:mm
        Regex("""(\d{1,2})\s*[:：]\s*(\d{2})\s*分?""").find(text)?.let { m ->
            return "${m.groupValues[1].toInt()}点${m.groupValues[2]}分许"
        }
        // 无分钟：X时/点（许/左右/前后），可带凌晨/上午/下午等前缀
        Regex("""(?:凌晨|早晨|早上|上午|中午|下午|傍晚|晚上|晚|夜里|夜间)?\s*(\d{1,2})\s*[点时]\s*(?:许|左右|前后)?""").find(text)?.let { m ->
            return "${m.groupValues[1].toInt()}时许"
        }
        return ""
    }

    /** 地点特征词：长词优先（含「管委会」「附近处」等完整行政区划链特征），保证最长匹配 */
    private val LOCATION_FEATURES = listOf(
        "门口处", "门前处", "附近处", "管委会", "门口", "门前", "路口", "交叉口", "交口",
        "立交", "桥下", "桥", "附近", "路段", "大道", "大街", "广场", "园区",
        "小区", "学校", "小学", "中学", "幼儿园", "医院", "商场", "车站", "处"
    ).sortedByDescending { it.length }
    private val LOCATION_FEATURE_PATTERN = LOCATION_FEATURES.joinToString("|")

    /** 组合模式（沿X…行驶至Y）的地点收尾特征：仅接受"完整地点"（附近处/管委会/交口等），
     * 排除"路口/交叉口/附近/门口/门前"等模糊位置词，避免抢占既有「沿路名」识别（如"沿长江路向东行驶至胜利路口"→"长江路"） */
    private val LOCATION_FULL_FEATURES = listOf(
        "门口处", "门前处", "附近处", "管委会", "立交", "桥下", "桥", "路段", "大道",
        "大街", "广场", "园区", "小区", "学校", "小学", "中学", "幼儿园", "医院", "商场", "车站", "处"
    ).sortedByDescending { it.length }
    private val LOCATION_FULL_PATTERN = LOCATION_FULL_FEATURES.joinToString("|")

    /** 解析地点：依次尝试 在/于、沿X（括号归属）…行驶至Y 组合、行驶至…、沿…、标点分段兜底（v1.63 强化） */
    fun parseLocation(text: String): String {
        // 0) 在/于 + 地点 + 特征词收尾（贪婪最长匹配）：
        //    "在安徽省合肥市肥西县紫蓬山管委会苗木大道附近处时" → 完整行政区划链（保留「处」）
        Regex("""(?:在|于|事发于|发生于)\s*([^，。,。\s时前左右许]{2,40})($LOCATION_FEATURE_PATTERN)(?:前|附近|，|。|,|\.)?""")
            .find(text)?.let { m ->
                val loc = (m.groupValues[1] + m.groupValues[2]).trim().trim('，', ',', '。', '的')
                if (loc.isNotBlank() && !loc.contains("时间") && !loc.contains("受伤") && !loc.contains("受损")) return loc
            }
        // 1) 在/于/事发于/发生于 + 位置 + 终止词（含"时"：明德小学门口时，与…）
        Regex("""(?:在|于|事发于|发生于)\s*([^，。,。\s]{2,40}?)(?=发生|相撞|碰撞|追尾|事故|造成|导致|与|行驶|驾车|骑车|掉头|转弯|直行|停车|后|时)""")
            .find(text)?.let { m ->
                val loc = m.groupValues[1].trim().trim('，', ',', '。', '的')
                if (loc.isNotBlank() && !loc.contains("时间") && !loc.contains("受伤") && !loc.contains("受损")) return loc
            }
        // 1.5) 沿X（括号行政归属）…由/向…行驶至Y 组合（v1.63）：并入括号归属得到完整行政区划链
        //    "沿合肥市苗木大道（肥西县紫蓬山管委会）由东向西行驶至白衣社区林场附近处" → "合肥市肥西县紫蓬山管委会白衣社区林场附近处"
        //    "沿合肥市苗木大道由东向西行驶至罗坝黄小郢附近处时" → "合肥市苗木大道罗坝黄小郢附近处"
        Regex(
            """沿\s*([^（(，。,。\s时前左右许]{2,40}?)\s*(?:（([^）)]{1,30})）|\(([^)]{1,30})\))?\s*(?:由|向|往|从)[^，。,。\s时前左右许]{0,12}?(?:行驶)?(?:至|到)\s*([^，。,。\s时前左右许]{2,40})($LOCATION_FULL_PATTERN)(?:前|附近|，|。|,|\.)?"""
        ).find(text)?.let { m ->
            val x = m.groupValues[1].trim()
            val bracket = m.groupValues[2].ifBlank { m.groupValues[3] }.trim()
            val y = (m.groupValues[4] + m.groupValues[5]).trim()
            // 括号内是行政归属（如"肥西县紫蓬山管委会"）：仅保留路名中的市级前缀与之拼接，避免"苗木大道"重复
            val base = if (bracket.isNotBlank()) {
                Regex("""^[\u4e00-\u9fa5]+?(?:市|州|区|县)""").find(x)?.value?.trim() ?: ""
            } else x
            val loc = (base + bracket + y).trim().trim('，', ',', '。', '的')
            if (loc.isNotBlank() && !loc.contains("时间") && !loc.contains("受伤") && !loc.contains("受损")) return loc
        }
        // 3) 沿X（路由/由…）行驶至Y —— 先于"行驶至"兜底，保证"沿长江路向东行驶至胜利路口"识别为路名"长江路"
        Regex("""沿\s*([^，。,。\s]{2,40}?)(?:由|向|往|从|东行|西行|南行|北行|行驶|行至|至|进入|途经|到达)""")
            .find(text)?.let { m ->
                val seg = m.groupValues[1].trim().trim('，', ',', '。', '的')
                if (seg.isNotBlank() && !seg.contains("时间") && !seg.contains("受伤") && !seg.contains("受损")) return seg
            }
        // 2) 行驶至/行至/到达/途经/至 + 地点（含特征词，如"…管委会苗木大道附近处"；无"沿"前缀的行驶语句）
        Regex("""(?:行驶至|行至|到达|途经|至)\s*([^，。,。\s]{2,40})($LOCATION_FEATURE_PATTERN)(?:前|附近|，|。|,|\.)?""")
            .find(text)?.let { m ->
                val loc = (m.groupValues[1] + m.groupValues[2]).trim().trim('，', ',', '。', '的')
                if (loc.isNotBlank() && !loc.contains("时间") && !loc.contains("受伤") && !loc.contains("受损")) return loc
            }
        // 4) 兜底：按标点切分，取含道路关键词的最长片段；排除"致X受伤/两车受损/交通事故"等结果性片段
        val roadKeywords = listOf(
            "市", "区", "县", "镇", "村", "路", "大道", "大街", "街", "巷",
            "桥", "路口", "立交", "高速", "快速路", "辅路", "园区", "工业园",
            "交叉口", "环岛", "匝道", "路段", "门口", "门前", "附近", "附近处",
            "管委会", "学校", "小学", "中学", "幼儿园", "医院", "广场", "小区",
            "商场", "车站", "处"
        )
        // 结果性/动作性片段特征：命中即排除（"致X受伤""两车受损""道路交通事故"等）
        val excludeMarkers = listOf(
            "受伤", "受损", "交通事故", "碰撞", "相撞", "追尾", "造成", "导致",
            "致", "伤亡", "死亡", "逃逸"
        )
        text.split(Regex("""[，。,。！？!?；;：:\s]"""))
            .filter { it.length in 2..40 }
            .filter { seg -> roadKeywords.any { it in seg } }
            .filter { seg -> excludeMarkers.none { it in seg } }
            .maxByOrNull { it.length }
            ?.trim()?.trim('，', ',', '。', '的')
            ?.takeIf { it.isNotBlank() && !it.contains("时间") }
            ?.let { return it }
        return ""
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

    /** 伤情否定词："无人员受伤"等应判定为未受伤 */
    private val INJURY_NEGATIONS = listOf(
        "无", "未", "没有", "无人", "并无", "并未", "不曾", "未造成", "无造成", "无人员"
    )

    /** 是否包含伤情描述（命中伤情关键词，且未被否定词修饰） */
    fun containsInjury(text: String): Boolean {
        for (kw in INJURY_KEYWORDS) {
            var idx = text.indexOf(kw)
            while (idx >= 0) {
                val before = text.substring((idx - 6).coerceAtLeast(0), idx)
                val negated = INJURY_NEGATIONS.any { before.contains(it) }
                if (!negated) return true
                idx = text.indexOf(kw, idx + 1)
            }
        }
        return false
    }

    /** 提取伤情短语：如 "致郭伟受伤" → "郭伟受伤"、"造成两人不同程度受伤" → "两人不同程度受伤"；跳过"无人员受伤"等否定表述 */
    fun parseInjuryDetail(text: String): String {
        // 优先取"致/造成/导致 + 伤情短语"
        Regex("""(?:致|造成|导致|致使|使得|撞伤|碰伤)\s*([^，。,。\s]{2,30}?(?:受伤|伤者|轻伤|重伤|骨折|昏迷|抢救|送医|流血|挫伤|擦伤|伤亡))""")
            .findAll(text).firstOrNull { m ->
                val before = text.substring((m.range.first - 6).coerceAtLeast(0), m.range.first)
                INJURY_NEGATIONS.none { before.contains(it) }
            }?.let { m ->
                return m.groupValues[1].trim()
            }
        // 兜底：取含"受伤"等关键词且未被否定的短句
        Regex("""[^，。,。\s]{1,20}?(?:受伤|伤者|轻伤|重伤|骨折|昏迷|抢救|送医|流血|挫伤|擦伤|伤亡)""")
            .findAll(text).firstOrNull { m ->
                val before = text.substring((m.range.first - 6).coerceAtLeast(0), m.range.first)
                INJURY_NEGATIONS.none { before.contains(it) }
            }?.let { m ->
                return m.value.trim()
            }
        return ""
    }

    /**
     * 解析当事人：
     *  1) "X驾驶/开车/骑车/驾车" → X（驾驶员）
     *  2) "与/和 X 发生/碰撞/相撞/追尾/受伤/驾驶" → X（对方当事人）
     *  3) "致 X 受伤/死亡/骨折" → X（伤者）
     *  4) 标点/介词后 姓氏开头人名 + 动作词 → X（兜底）
     */
    fun parseParties(text: String): List<String> {
        val names = mutableListOf<String>()

        fun clean(name: String): String? {
            var candidate = name.trim()
            // 尾部去掉动作/虚词/方位字（如 "郭伟驾" → "郭伟"、"李四撞" → "李四"、"赵敏相" → "赵敏"、"东向西" → 剔除）
            candidate = candidate.trimEnd(
                '驾', '驶', '开', '车', '骑', '行', '发', '生', '碰', '撞',
                '追', '尾', '受', '伤', '肇', '到', '往', '至', '向', '走',
                '相', '东', '西', '南', '北'
            )
            // 再从长到短截断，遇到不在停用词中的片段即停
            for (cut in minOf(3, candidate.length) downTo 2) {
                val sub = candidate.substring(0, cut)
                if (!STOP_WORDS.contains(sub)) {
                    candidate = sub
                    break
                }
            }
            if (candidate.length in 2..3 && !STOP_WORDS.contains(candidate)) {
                val first = candidate.first()
                if (first in COMMON_SURNAMES) return candidate
            }
            return null
        }

        // 模式1：X驾驶/开车/骑车/驾车（最可靠的当事人信号）
        // v1.81：候选放宽到 2-6 字，支持「醉酒后/酒后/饮酒后驾驶」等修饰语夹在姓名与「驾驶」之间
        Regex("""([\u4e00-\u9fa5]{2,6})(?=驾驶|开车|骑车|驾车)""").findAll(text).forEach { m ->
            // 驾驶员名字通常以姓氏开头，从候选串里按姓氏回溯取最长人名
            // v1.74：先剥离方位词前缀（前方/后方/左侧/右侧/左边/右边/旁边/对向等），
            // 避免「前方郑露驾驶」把方位词的「方」误当姓氏识别成「方郑露」
            var raw = m.groupValues[1]
            DIRECTION_PREFIXES.firstOrNull { raw.startsWith(it) }?.let {
                raw = raw.removePrefix(it)
            }
            // v1.81：再剥离「醉酒后/酒后/饮酒后/醉酒/饮酒」等驾驶状态修饰语，
            // 避免「汪霞醉酒后驾驶」因修饰词夹在姓名与「驾驶」之间导致姓名提取失败
            DRINK_MODIFIERS.firstOrNull { raw.endsWith(it) }?.let {
                raw = raw.removeSuffix(it)
            }
            var best: String? = null
            for (start in 0 until raw.length) {
                if (raw[start] in COMMON_SURNAMES) {
                    val tail = raw.substring(start)
                    clean(tail)?.let { if (it.length > (best?.length ?: 0)) best = it }
                }
            }
            best?.let { names.add(it) }
        }

        // 模式2：与/和 + 人名 + 动作
        Regex("""[与和]([\u4e00-\u9fa5]{2,3})(?=[发生碰撞相撞追尾受伤肇驾驶])""").findAll(text).forEach { m ->
            clean(m.groupValues[1])?.let { names.add(it) }
        }

        // 模式3：致/造成/导致 + 人名 + 受伤/死亡
        Regex("""(?:致|造成|导致|致使|使得)\s*([\u4e00-\u9fa5]{2,3})(?=受伤|死亡|重伤|轻伤|昏迷|骨折|抢救|送医|流血)""")
            .findAll(text).forEach { m ->
                clean(m.groupValues[1])?.let { names.add(it) }
            }

        // 模式4：标点/介词后 姓氏开头人名 + 动作（兜底）
        val boundary = "(?:^|[，。：:、\\s的由向至在从与和到往])([$COMMON_SURNAMES][\\u4e00-\\u9fa5]{1,2})(?=[发生碰撞相撞追尾受伤肇驾驶开车骑车驾车行驶])"
        val matcher = Pattern.compile(boundary).matcher(text)
        while (matcher.find()) {
            matcher.group(1)?.let { g ->
                clean(g)?.let { names.add(it) }
            }
        }

        return names.distinct().take(6)
    }
}
