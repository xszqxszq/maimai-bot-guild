package xyz.xszq

import com.soywiz.kds.iterators.fastForEach
import com.soywiz.kds.iterators.fastForEachWithIndex
import com.soywiz.kds.mapDouble
import com.soywiz.korio.util.toStringDecimal
import io.ktor.http.*
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import net.mamoe.mirai.utils.info
import xyz.xszq.MaimaiBot.aliases
import xyz.xszq.MaimaiBot.musics
import xyz.xszq.MaimaiBot.stats
import xyz.xszq.gocqhttp.GlobalGuildEventChannel
import xyz.xszq.gocqhttp.GuildMessageEvent
import xyz.xszq.gocqhttp.quoteReply
import xyz.xszq.gocqhttp.uploadAsGuildImage

object MaimaiBotGuild : KotlinPlugin(
    JvmPluginDescription(
        id = "xyz.xszq.maimai-bot-guild",
        name = "MaimaiBot-GuildExtension",
        version = "0.1.0",
    ) {
        author("xszqxszq")
    }
) {
    override fun onEnable() {
        GlobalGuildEventChannel.subscribeMessages {
            startsWith("/b40") { username ->
                queryBest("username", username, false, this)
            }
            startsWith("/b50") { username ->
                queryBest("username", username, true, this)
            }
            startsWith("/随个") { raw ->
                if (raw.isNotEmpty()) {
                    var difficulty: Int? = -1
                    val level = if (!raw[0].isDigit()) {
                        difficulty = MaimaiImage.name2Difficulty(raw[0])
                        raw.substring(1).filter { it.isDigit() || it == '+' }
                    } else {
                        raw.filter { it.isDigit() || it == '+' }
                    }
                    difficulty?.let {
                        getRandom(difficulty, level, this)
                    }
                }
            }
            startsWith("/id") { id ->
                searchById(id.filter { it.isDigit() }, this)
            }
            startsWith("/查歌") { name ->
                searchByName(name, this)
            }
            startsWith("/定数查歌") { rawArgs ->
                val args = rawArgs.toArgsList().mapDouble { it.toDouble() }
                if (args.size == 1)
                    searchByDS(args.first()..args.first(), this)
                else
                    searchByDS(args.first()..args.last(), this)
            }
            startsWith("/分数线") { rawArgs ->
                val args = rawArgs.toArgsList()
                getScoreRequirements(args, this)
            }
            endsWith("是什么歌") { alias ->
                if (alias.startsWith("/mai "))
                    searchByAlias(alias.substringAfter("/mai "), this)
            }
            endsWith("有什么别名") { id ->
                if (id.startsWith("/mai "))
                searchAliasById(id.substringAfter("/mai ").filter { it.isDigit() }, this)
            }
        }
        arrayOf("绿", "黄", "红", "紫", "白").fastForEachWithIndex { difficulty, str ->
            GlobalGuildEventChannel.subscribeMessages {
                startsWith("/" + str + "id") { id ->
                    searchByIdAndDifficulty(id, difficulty, this)
                }
            }
        }
        logger.info { "Plugin loaded" }
    }
    private suspend fun queryBest(type: String = "qq", id: String, b50: Boolean, event: GuildMessageEvent) = event.run {
        if (id.isBlank()) {
            quoteReply("频道暂不支持查 QQ 号，请使用/b${if (b50) 5 else 4}0 用户名")
            return@run
        }
        val result = DXProberApi.getPlayerData(type, id, b50)
        when (result.first) {
            HttpStatusCode.OK -> {
                MaimaiImage.generateBest(result.second!!, b50).toExternalResource().use { img ->
                    quoteReply(img.uploadAsGuildImage())
                }
            }
            HttpStatusCode.BadRequest -> quoteReply("用户名不存在，请确认用户名对应的玩家在 Diving-Fish 的舞萌 DX 查分器" +
                    "（https://www.diving-fish.com/maimaidx/prober/）上已注册")
            HttpStatusCode.Forbidden -> quoteReply("该玩家已禁止他人查询成绩")
        }
        return@run
    }
    private suspend fun searchById(id: String, event: GuildMessageEvent) = event.run {
        musics[id] ?.let { selected ->
            quoteReply(getMusicInfoForSend(selected, this).build())
        }
    }
    private suspend fun searchByIdAndDifficulty(id: String, difficulty: Int, event: GuildMessageEvent) = event.run {
        musics[id] ?.let { selected ->
            if (selected.level.size <= difficulty)
                return@run
            val chart = selected.charts[difficulty]
            val result = MessageChainBuilder()
            result.add("${selected.id}. ${selected.title}\n")
            MaimaiImage.resolveCoverFileOrNull(selected.id) ?.let {
                it.toExternalResource().use { png ->
                    result.add(png.uploadAsGuildImage())
                }
            }
            result.add("\n等级: ${selected.level[difficulty]} (${selected.ds[difficulty]})")
            MaimaiChartNotes.fromList(chart.notes) ?.let { notes ->
                result.add("\nTAP: ${notes.tap}\nHOLD: ${notes.hold}")
                result.add("\nSLIDE: ${notes.slide}")
                notes.touch ?.let { touch -> result.add("\nTOUCH: $touch") }
                result.add("\nBREAK: ${notes.break_}")
                if (chart.charter != "-")
                    result.add("\n谱师：${chart.charter}")
                result.add("\n难易度：${stats[id]!![difficulty].tag}")
                result.add("\n平均达成率：${stats[id]!![difficulty].avg!!.toStringDecimal(2)}%")
            }
            quoteReply(result.build())
        }
    }
    private suspend fun searchByName(name: String, event: GuildMessageEvent) = event.run {
        val list = musics.values.filter { name.lowercase() in it.basic_info.title.lowercase() }.take(50)
        if (list.isEmpty()) {
            quoteReply("未搜索到歌曲，请检查拼写。如需搜索别称请发送“XXX是什么歌”")
        } else {
            var result = ""
            list.forEach { result += "${it.id}. ${it.basic_info.title}\n" }
            quoteReply(result)
        }
    }
    private suspend fun searchByAlias(alias: String, event: GuildMessageEvent) = event.run {
        val names = aliases.filter { (_, value) ->
            var matched = false
            value.fastForEach inner@ {
                if (alias.lowercase().trim() == it.lowercase()) {
                    matched = true
                    return@inner
                }
            }
            matched
        }
        val matched = musics.values.filter { it.title in names.keys }
        when {
            matched.size > 1 -> {
                var result = "您要找的歌曲可能是："
                matched.fastForEach { result += "\n" + it.id + ". " + it.title }
                quoteReply(result)
            }
            matched.isNotEmpty() -> {
                val selected = matched.first()
                val result = MessageChainBuilder()
                result.add("您要找的歌曲可能是：\n")
                quoteReply(getMusicInfoForSend(selected, this, result).build())
            }
            else -> {
                quoteReply("未找到歌曲。您可以联系 bot 号主添加新的别名，" +
                        "或访问此网址添加：https://docs.qq.com/sheet/DWGNNYUdTT01PY2N1\n" +
                        "如果要按名称搜索，请使用如下命令：\n\t查歌 歌曲名称")
            }
        }
    }
    private suspend fun searchByDS(range: ClosedFloatingPointRange<Double>, event: GuildMessageEvent) = event.run {
        var result = ""
        musics.values.filter { it.ds.any { now -> now in range } }.take(50).fastForEach { selected ->
            val difficulties = mutableListOf<Int>()
            selected.ds.fastForEachWithIndex { index, value ->
                if (value in range)
                    difficulties.add(index)
            }
            difficulties.forEach { difficulty ->
                result += "${selected.id}. ${selected.title} ${MaimaiImage.difficulty2Name(difficulty)} " +
                        "${selected.level[difficulty]} (${selected.ds[difficulty]})\n"
            }
        }
        quoteReply(if (result == "") "没有找到歌曲。\n使用方法：\n\t定数查歌 定数\n\t定数查歌 下限 上限" else result)
    }
    private suspend fun searchAliasById(id: String, event: GuildMessageEvent) = event.run {
        musics[id] ?.let { selected ->
            val nowAliases = aliases[selected.title]
            if (nowAliases?.isNotEmpty() == true)
                quoteReply("$id. ${selected.title} 有如下别名：\n" + nowAliases.joinToString("\n"))
            else
                quoteReply("这首歌似乎没有别名呢。\n您可以联系 bot 号主添加别名，" +
                        "或访问此网址添加：https://docs.qq.com/sheet/DWGNNYUdTT01PY2N1")
        }
    }


    private suspend fun getRandom(difficulty: Int = -1, level: String, event: GuildMessageEvent) = event.run {
        val selected = musics.values.filter {
            (level in it.level && it.level.size > difficulty && difficulty != -1 && it.level[difficulty] == level)
                    || (level == "" && it.level.size > difficulty)
                    || (difficulty == -1 && level in it.level)}.randomOrNull()
        selected ?.let {
            quoteReply(getMusicInfoForSend(it, this).build())
        } ?: run {
            quoteReply("没有这样的乐曲。")
        }
    }
    private suspend fun getScoreRequirements(args: List<String>, event: GuildMessageEvent) = event.run {
        when {
            args.size == 2 && args[1].toDoubleOrNull() != null && (args[1].toDouble() in 0.0..101.0) -> {
                MaimaiImage.name2Difficulty(args[0][0]) ?.let { difficulty ->
                    args[0].filter { it.isDigit() }.toIntOrNull() ?.let { id ->
                        musics[id.toString()] ?.let { target ->
                            if (target.level.size <= difficulty) {
                                quoteReply("该谱面没有此难度。请输入“分数线 帮助”查看使用说明。")
                            } else {
                                val line = args[1].toDouble()
                                val notes = MaimaiChartNotes.fromList(target.charts[difficulty].notes)!!
                                val totalScore = notes.tap * 500.0 + notes.hold * 1000 + notes.slide * 1500 +
                                        (notes.touch ?: 0) * 500 + notes.break_ * 2500
                                val breakBonus = 0.01 / notes.break_
                                val break50Reduce = totalScore * breakBonus / 4
                                val reduce = 101.0 - line
                                quoteReply("[${args[0][0]}] ${target.title}\n" +
                                        "分数线 $line% 允许的最多 TAP GREAT 数量为 " +
                                        String.format("%.2f", totalScore * reduce / 10000) +
                                        " (每个 -" + String.format("%.4f", 10000.0 / totalScore) + "%),\n" +
                                        "BREAK 50落 (一共 ${notes.break_} 个) 等价于 " +
                                        String.format("%.3f", break50Reduce / 100) + " 个 TAP GREAT " +
                                        "(-" + String.format("%.4f", break50Reduce / totalScore * 100) + "%)")
                            }
                        } ?: run {
                            quoteReply("未找到该谱面。请输入“分数线 帮助”查看使用说明。")
                        }
                    }
                } ?: run {
                    quoteReply("格式错误，请输入“分数线 帮助”查看使用说明。")
                }
            }
            args.size == 1 && args.first() in listOf("帮助", "help") ->
                quoteReply(
                    "此功能为查找某首歌分数线设计。\n" +
                            "命令格式：分数线 <难度+歌曲id> <分数线>\n" +
                            "例如：分数线 紫379 100.5\n" +
                            "命令将返回分数线允许的 TAP GREAT 容错以及 BREAK 50落等价的 TAP GREAT 数。\n" +
                            "以下为 TAP GREAT 的对应表：\n" +
                            "GREAT/GOOD/MISS\n" +
                            "TAP   1/2.5/5\n" +
                            "HOLD  2/5/10\n" +
                            "SLIDE 3/7/15\n" +
                            "TOUCH 1/2/5\n" +
                            "BREAK 5/12.5/25(外加200落)"
                )
            else -> quoteReply("格式错误，请输入“分数线 帮助”查看使用说明。")
        }
    }
    private suspend fun getMusicInfoForSend(
        selected: MaimaiMusicInfo, event: GuildMessageEvent, builder: MessageChainBuilder = MessageChainBuilder())
            = event.run {
        builder.add(selected.id + ". " + selected.title + "\n")
        MaimaiImage.resolveCoverFileOrNull(selected.id) ?.let {
            it.toExternalResource().use { png ->
                builder.add(png.uploadAsGuildImage())
            }
        }
        builder.add("\n艺术家：${selected.basic_info.artist}" +
                "\n分类：${selected.basic_info.genre}" +
                "\n版本：${selected.basic_info.from}" + (if (selected.basic_info.is_new) " （计入b15）" else "") +
                "\nBPM：${selected.basic_info.bpm}" +
                "\n定数：" + selected.ds.joinToString("/")
        )
        builder
    }
    suspend fun getMusicBriefForSend(
        selected: MaimaiMusicInfo, event: GuildMessageEvent, builder: MessageChainBuilder = MessageChainBuilder())
            = event.run {
        builder.add(selected.id + ". " + selected.title + "\n")
        MaimaiImage.resolveCoverFileOrNull(selected.id) ?.let {
            it.toExternalResource().use { png ->
                builder.add(png.uploadAsGuildImage())
            }
        }
        builder.add("\n艺术家：${selected.basic_info.artist}" + "\n定数：" + selected.ds.joinToString("/"))
        builder
    }
}
