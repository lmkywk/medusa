package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.ui.graphics.Color
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ModelField
import io.github.aw1y2z.sesame.data.ModelGroup
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField
import io.github.aw1y2z.sesame.util.Log

internal enum class DomainCardType { ENTRY, SWITCH, TASK, ACTION }

internal data class DomainCardSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: DomainCardType,
    val accent: Color,
    /** 只切换这个字段；为空时沿用“切换本卡全部布尔开关”。 */
    val switchCode: String? = null,
    val match: (ModelField<*>) -> Boolean
)

internal data class DomainSpec(
    val key: String,
    val title: String,
    val group: ModelGroup,
    val cards: List<DomainCardSpec>,
    /** 只显示匹配到的字段，不把同组的其它字段收进第一张卡（古树与生态保护共用 PROTECT 组） */
    val onlyMatched: Boolean = false,
    /** 本域的主模型（enable 等域级开关只认它，避免同组其它模型抢占） */
    val primaryModelCode: String? = null,
    /** 同组中属于其它域的字段，不参与本域页 */
    val exclude: (ModelField<*>) -> Boolean = { false }
)

internal object DomainRegistry {

    private fun name(field: ModelField<*>): String = field.name ?: ""
    private fun code(field: ModelField<*>): String = field.code ?: ""

    /** 森林「好友」卡的分段：卡片详情页按这里的顺序分块显示，未列出的字段不进分段。 */
    private val friendSections: Map<String, Set<String>> = linkedMapOf(
        "收能量" to setOf(
            "dontCollectList", "helpFriendCollectType", "helpFriendCollectList",
            "helpFriendCollectListLimit", "collectGiftBox"
        ),
        "浇水" to setOf(
            "waterFriendEnable", "waterMode", "waterFriendType", "waterDailyLimit", "waterFriendList",
            "waterFriendEnergySendChat", "waterFriendEnergyFirst", "doubleWaterFriendEnergy"
        ),
        "合种" to setOf(
            "partnerteamWater", "partnerteamWaterNum", "partnerteamWaterUntil",
            "loveteamWater", "loveteamWaterNum"
        ),
        "寻宝互助" to setOf("ForestHunt", "ForestHuntDraw", "ForestHuntHelp", "ForestHuntHelpList")
    )

    private val FRIEND_CARD_CODES: Set<String> = friendSections.values.flatten().toSet()

    fun sectionOrder(cardId: String): List<String> =
        if (cardId == "forest.friends") friendSections.keys.toList() else emptyList()

    fun sectionOf(cardId: String, code: String?): String? {
        if (cardId != "forest.friends" || code == null) return null
        return friendSections.entries.firstOrNull { code in it.value }?.key
    }

    private val forestCards = listOf(
        // 兜底卡：未被下面 8 张卡匹配的字段都在这里，点箭头进入设置页
        DomainCardSpec("forest.settings", "森林设置", "", DomainCardType.ENTRY, MedusaColors.GreenDeep) { false },
        DomainCardSpec("forest.home", "收能量", "", DomainCardType.SWITCH, MedusaColors.Green) { f ->
            code(f) in setOf(
                "collectEnergy", "batchRobEnergy", "CollectSelfEnergyType",
                "CollectSelfEnergyThreshold", "collectWateringBubble", "collectRobExpandEnergyEnable",
                "collectRobExpandEnergy", "expiredEnergy", "pkEnergy", "energyPvp",
                "CollectBombEnergyLimit"
            )
        },
        // 好友：收能量 / 浇水 / 合种 / 寻宝互助共用一张卡，名单统一走好友中心的常用名单
        DomainCardSpec("forest.friends", "好友", "收能量 · 浇水 · 合种 · 互助",
            DomainCardType.SWITCH, MedusaColors.Blue) { f ->
            code(f) in FRIEND_CARD_CODES
        },
        DomainCardSpec("forest.green", "绿色能量", "", DomainCardType.SWITCH, MedusaColors.Green) { f ->
            code(f) in setOf("greenLife", "marketBrowseTask", "greenRent", "readForest")
        },
        DomainCardSpec("forest.reward", "活力值", "", DomainCardType.TASK, MedusaColors.Gold,
            switchCode = "receiveForestTaskAward", match = { f ->
                code(f) in setOf(
                    "receiveForestTaskAward", "AutoAntForestVitalityTaskList", "AntForestVitalityTaskList",
                    "vitalityExchangeBenefit", "vitality_ExchangeBenefitList", "youthPrivilege",
                    "medicalHealth"
                )
            }),
        DomainCardSpec("forest.seckill", "活力值秒杀", "探针 / 自动抢", DomainCardType.SWITCH, MedusaColors.Gold) { f ->
            code(f) in setOf("vitalitySeckillProbe", "vitalitySeckillAutoBuy")
        },
        DomainCardSpec("forest.park", "森林乐园", "", DomainCardType.SWITCH, MedusaColors.Blue) { f ->
            code(f) == "drawGameCenterAward"
        },
        DomainCardSpec("forest.patrol", "保护地巡护", "", DomainCardType.SWITCH, MedusaColors.GreenDeep) { f ->
            code(f) in setOf(
                "userPatrol", "monopolyPatrol", "monopolyTasks", "monopolyAnimalEnergy",
                "monopolyDispatchAnimal", "monopolyDispatchPriority", "AutoMonopolyTaskList",
                "MonopolyTaskList", "consumeAnimalPropType"
            )
        },
        DomainCardSpec("forest.backpack", "背包与道具", "", DomainCardType.SWITCH, MedusaColors.Gold) { f ->
            code(f) in setOf(
                "collectProp", "whoYouWantToGiveTo", "continuousUseCardOptions",
                "autoUseShieldCard", "continuousUseShieldHour", "energyRain", "giveEnergyRainList",
                "useEnergyRainLimit",
                "dress"
            )
        },
        DomainCardSpec("forest.ip", "IP联名馆", "", DomainCardType.SWITCH, MedusaColors.Gold) { f ->
            code(f) in setOf("ipExchange", "ipExchangeCount")
        }
    )

    private val greenActionCards = listOf(
        DomainCardSpec("green.settings", "绿色行动设置", "开启 / 打卡选项 / 数据", DomainCardType.ENTRY, MedusaColors.GreenDeep) { f ->
            val c = code(f)
            c == "enable" || c == "ecoLife" || c == "ecoLifeOptions"
        },
        DomainCardSpec("green.grain", "节约粮食", "2 个动作 · 光盘需拍照", DomainCardType.SWITCH, MedusaColors.Gold) { code(it) == "ecoLifeGrain" },
        DomainCardSpec("green.travel", "绿色出行", "4 个动作", DomainCardType.SWITCH, MedusaColors.Green) { code(it) == "ecoLifeTravel" },
        DomainCardSpec("green.electricity", "节约用电", "5 个动作", DomainCardType.SWITCH, MedusaColors.Blue) { code(it) == "ecoLifeElectricity" },
        DomainCardSpec("green.water", "节约用水", "6 个动作", DomainCardType.SWITCH, MedusaColors.Blue) { code(it) == "ecoLifeWater" },
        DomainCardSpec("green.recycle", "变废为宝", "3 个动作", DomainCardType.SWITCH, MedusaColors.Purple) { code(it) == "ecoLifeRecycle" },
        DomainCardSpec("green.paper", "减纸减塑", "5 个动作", DomainCardType.SWITCH, MedusaColors.GreenDeep) { code(it) == "ecoLifePaper" }
    )

    private val oceanCards = listOf(
        DomainCardSpec("ocean.settings", "本域设置", "启用 / 更多设置", DomainCardType.ENTRY, MedusaColors.GreenDeep,
            switchCode = "enable", match = { f -> code(f) == "enable" }),
        DomainCardSpec("ocean.reward", "任务奖励", "", DomainCardType.TASK, MedusaColors.Gold,
            switchCode = "queryTaskList", match = { f ->
                code(f) in setOf("queryTaskList", "AutoAntOceanAntiepTaskList", "AntOceanAntiepTaskList")
            }),
        DomainCardSpec("ocean.fish", "捞鱼", "", DomainCardType.SWITCH, MedusaColors.Blue,
            switchCode = "antfishEnable", match = { f ->
                code(f) in setOf("antfishEnable", "antfishAutoTask", "AutoAntOceanFishBlackList")
            }),
        DomainCardSpec("ocean.clean", "清理", "", DomainCardType.ACTION, MedusaColors.Purple) { f ->
            code(f) in setOf("cleanMorning", "cleanEvening", "cleanFriends")
        },
        DomainCardSpec("ocean.welcome", "找回", "", DomainCardType.SWITCH, MedusaColors.Green,
            switchCode = "autoWelcome", match = { f ->
                code(f) in setOf("autoWelcome", "exchangeUniversalPiece", "useUniversalPiece")
            })
    )

    private val farmCards = listOf(
        DomainCardSpec("farm.settings", "本域设置", "启用 / 更多设置", DomainCardType.ENTRY, MedusaColors.GreenDeep,
            switchCode = "enable", match = { f -> code(f) == "enable" }),
        DomainCardSpec("farm.daily", "庄园日常", "", DomainCardType.SWITCH, MedusaColors.Gold) { f ->
            code(f) in setOf(
                "feedAnimal", "harvestProduce", "useNewEggTool", "useAccelerateTool",
                "useAccelerateToolOptions", "useBigEaterTool", "useSpecialFood",
                "useSpecialFoodCountLimit", "acceptGift", "visitFriendList",
                "receiveFarmToolReward"
            )
        },
        DomainCardSpec("farm.task", "饲料任务", "", DomainCardType.TASK, MedusaColors.Green,
            switchCode = "receiveFarmTaskAward", match = { f ->
                code(f) in setOf("receiveFarmTaskAward", "AutoAntFarmDoFarmTaskList", "AntFarmDoFarmTaskList")
            }),
        DomainCardSpec("farm.donate", "每日捐蛋", "", DomainCardType.ACTION, MedusaColors.Purple) { f ->
            code(f) in setOf("donationType", "donationAmount")
        },
        DomainCardSpec("farm.park", "小鸡乐园", "", DomainCardType.SWITCH, MedusaColors.Blue) { f ->
            code(f) in setOf("recordFarmGame", "farmGameTime", "drawGameCenterAward",
                "gameCenterBuyMallItem", "gameCenterBuyMallItemList")
        },
        DomainCardSpec("farm.draw", "装扮抽奖", "", DomainCardType.TASK, MedusaColors.Gold,
            switchCode = "drawMachine", match = { f ->
                code(f) in setOf("drawMachine", "AutoAntFarmDrawMachineTaskList",
                    "AntFarmDrawMachineTaskList", "IPexchangeBenefit")
            }),
        DomainCardSpec("farm.competition", "爱心鸡活动", "", DomainCardType.SWITCH, MedusaColors.Purple) { f ->
            code(f) in setOf("competition", "competitionReceiveTask", "competitionDonate",
                "competitionDonateAmount", "competitionStealRank", "competitionStealMinutes",
                "competitionStealLimit")
        },
        DomainCardSpec("farm.sunflower", "加油小葵", "", DomainCardType.SWITCH, MedusaColors.Gold) { f ->
            code(f) in setOf("sunflowerDonate", "sunflowerKeep")
        },
        DomainCardSpec("farm.family", "家庭空间", "", DomainCardType.SWITCH, MedusaColors.Green) { f ->
            code(f) in setOf("family", "familyOptions", "notInviteList")
        },
        DomainCardSpec("farm.friend", "好友互动", "", DomainCardType.SWITCH, MedusaColors.Blue) { f ->
            code(f) in setOf("feedFriendAnimal", "feedFriendAnimalList", "hireAnimalType",
                "hireAnimalList", "recallAnimalType", "sendBackAnimalWay", "sendBackAnimalType",
                "sendFriendList", "notifyFriendType", "notifyFriendList", "rewardFriend")
        },
        DomainCardSpec("farm.kitchen", "小鸡厨房", "", DomainCardType.SWITCH, MedusaColors.Gold) { f ->
            code(f) == "kitchen"
        },
        DomainCardSpec("farm.life", "小鸡生活", "", DomainCardType.SWITCH, MedusaColors.GreenDeep) { f ->
            code(f) in setOf("chickenDiary", "enableSleep", "sleepTime", "sleepMinutes",
                "ornamentsDressUp", "ornamentsDressUpList", "ornamentsDressUpDays")
        }
    )

    private val orchardCards = listOf(
        DomainCardSpec("orchard.settings", "本域设置", "启用 / 更多设置", DomainCardType.ENTRY, MedusaColors.GreenDeep,
            switchCode = "enable", match = { f -> code(f) in setOf("enable", "executeInterval") }),
        DomainCardSpec("orchard.fertilizer", "每日肥料", "", DomainCardType.SWITCH, MedusaColors.Gold,
            switchCode = "collectFertilizerPacket") { f ->
            code(f) == "collectFertilizerPacket"
        },
        DomainCardSpec("orchard.task", "肥料任务", "", DomainCardType.TASK, MedusaColors.Green,
            switchCode = "orchardListTask", match = { f ->
                code(f) in setOf("orchardListTask", "AutoAntOrchardTaskList", "AntOrchardTaskList")
            }),
        DomainCardSpec("orchard.sign", "每日签到", "", DomainCardType.SWITCH, MedusaColors.Purple,
            switchCode = "orchardSign") { f ->
            code(f) == "orchardSign"
        },
        DomainCardSpec("orchard.spread", "自动施肥", "", DomainCardType.SWITCH, MedusaColors.Blue,
            switchCode = "orchardSpreadManure") { f ->
            code(f) in setOf(
                "orchardSpreadManure", "spreadMode", "orchardSpreadManureSceneList",
                "orchardSpreadManureCount"
            )
        },
        DomainCardSpec("orchard.park", "农场乐园", "", DomainCardType.TASK, MedusaColors.Gold,
            switchCode = "drawGameCenterAward", match = { f ->
                code(f) in setOf("drawGameCenterAward", "receiveOrchardGameStay")
            }),
        DomainCardSpec("orchard.assist", "好友助力", "", DomainCardType.SWITCH, MedusaColors.Blue,
            switchCode = "assistFriend") { f ->
            code(f) in setOf("assistFriend", "assistFriendList")
        },
        DomainCardSpec("orchard.draw", "阿肥寻宝记", "", DomainCardType.TASK, MedusaColors.Purple,
            switchCode = "orchardChouChouLe") { f ->
            code(f) in setOf("orchardChouChouLe", "AutoOrchardChouChouLeTaskList", "OrchardChouChouLeTaskList")
        },
        DomainCardSpec("orchard.challenge", "限时挑战", "", DomainCardType.TASK, MedusaColors.Gold,
            switchCode = "orchardChallenge") { f ->
            code(f) == "orchardChallenge"
        },
        DomainCardSpec("orchard.factory", "肥料罐", "", DomainCardType.SWITCH, MedusaColors.Green,
            switchCode = "collectManure") { f ->
            code(f) == "collectManure"
        },
        // 只读信息卡：没有可配置字段，卡面直接显示果树状态/组队状态
        DomainCardSpec("orchard.tree", "果树状态", "", DomainCardType.ACTION, MedusaColors.GreenDeep) { false },
        DomainCardSpec("orchard.team", "合种状态", "", DomainCardType.ACTION, MedusaColors.Blue) { false }
    )

    private val protectCards = listOf(
        DomainCardSpec("protect.settings", "本域设置", "启用 / 更多设置", DomainCardType.ENTRY, MedusaColors.GreenDeep,
            switchCode = "enable", match = { f -> code(f) == "enable" }),
        DomainCardSpec("protect.ancient", "古树保护", "", DomainCardType.SWITCH, MedusaColors.Gold) { f ->
            code(f).startsWith("ancientTree")
        },
        DomainCardSpec("protect.cooperate", "合种浇水", "", DomainCardType.SWITCH, MedusaColors.Green) { f ->
            code(f) in setOf("cooperateWater", "cooperateWaterList", "cooperateWaterTotalLimitList")
        },
        DomainCardSpec("protect.carbon", "碳中和行动", "", DomainCardType.SWITCH, MedusaColors.Blue) { f ->
            code(f) in setOf(
                "protectMarathonType", "protectMarathonList",
                "protectNewAncientTreeType", "protectNewAncientTreeList"
            )
        },
        DomainCardSpec("protect.forest", "保护森林", "", DomainCardType.SWITCH, MedusaColors.GreenDeep) { f ->
            code(f) in setOf("protectTree", "protectTreeList")
        },
        DomainCardSpec("protect.animal", "保护动物", "", DomainCardType.SWITCH, MedusaColors.Purple) { f ->
            code(f) in setOf(
                "protectReserve", "reserveList", "protectReserveMinNum", "protectReserveNum",
                "protectAnimal", "protectAnimalList"
            )
        },
        DomainCardSpec("protect.ocean", "保护海洋", "", DomainCardType.SWITCH, MedusaColors.Blue) { f ->
            code(f) in setOf(
                "protectBeachMinNum", "protectBeachNum", "protectBeach", "protectOceanList"
            )
        }
    )

    private val ancientCards = listOf(
        DomainCardSpec("ancient.settings", "古树设置", "仅周一三五 / 区划代码", DomainCardType.SWITCH, MedusaColors.GreenDeep) { f ->
            code(f).startsWith("ancientTree")
        }
    )

    private val speciesCards = listOf(
        DomainCardSpec("species.settings", "本域设置", "启用 / 更多设置", DomainCardType.ENTRY, MedusaColors.GreenDeep,
            switchCode = "enable", match = { f -> code(f) == "enable" }),
        DomainCardSpec("species.task", "物种任务", "", DomainCardType.TASK, MedusaColors.Gold,
            switchCode = "dodoTaskList", match = { f ->
                code(f) in setOf("dodoTaskList", "AutoAntDodoTaskList", "AntDodoTaskList")
            }),
        DomainCardSpec("species.draw", "每日抽卡", "", DomainCardType.SWITCH, MedusaColors.Green) { f ->
            code(f) == "scratchCard"
        },
        DomainCardSpec("species.friend", "帮好友抽卡", "", DomainCardType.ACTION, MedusaColors.Blue) { f ->
            code(f) in setOf("collectToFriendType", "collectToFriendList")
        },
        DomainCardSpec("species.friends", "好友名单", "", DomainCardType.ACTION, MedusaColors.Blue) { f ->
            code(f) == "speciesFriendList"
        },
        DomainCardSpec("species.prop", "使用道具", "", DomainCardType.SWITCH, MedusaColors.Purple) { f ->
            code(f) in setOf(
                "useProp", "usePropList", "useCollectTimingType",
                "useUniversalCardBookStatusType", "useUniversalCardBookCollectedStatusType",
                "useUniversalCardBookCollectedhasCollected", "useUniversalCardMedalGenerationStatusType",
                "useUniversalCardFantasticLevelType"
            )
        },
        DomainCardSpec("species.book", "图鉴与勋章", "", DomainCardType.SWITCH, MedusaColors.Gold) { f ->
            code(f) in setOf("bookMedal", "bookMedalOptions")
        },
        DomainCardSpec("species.gift", "赠送卡片", "", DomainCardType.SWITCH, MedusaColors.Blue) { f ->
            code(f) in setOf(
                "giftToFriend", "giftToFriendBookStatusType", "giftToFriendBookCollectedStatusType",
                "giftToFriendMedalGenerationStatusType", "giftToFriendFantasticLevelType", "giftToFriendList"
            )
        }
    )

    private val sportsCards = listOf(
        DomainCardSpec("sports.settings", "本域设置", "启用 / 更多设置", DomainCardType.ENTRY, MedusaColors.GreenDeep,
            switchCode = "enable", match = { code(it) == "enable" }),
        DomainCardSpec("kinetic.center", "健康能量", "余额 / 今日可领", DomainCardType.ACTION, MedusaColors.Gold) { false },
        DomainCardSpec("kinetic.sign", "每日签到", "", DomainCardType.SWITCH, MedusaColors.Purple,
            switchCode = "kineticSignIn") { f -> code(f) == "kineticSignIn" },
        DomainCardSpec("kinetic.task", "能量任务", "", DomainCardType.TASK, MedusaColors.Green,
            switchCode = "sportsTasks", match = { f ->
                code(f) in setOf("sportsTasks", "AutoAntSportsTaskList", "AntSportsTaskList")
            }),
        DomainCardSpec("kinetic.bubble", "运动币气泡", "收集运动币", DomainCardType.SWITCH, MedusaColors.Blue,
            switchCode = "receiveCoinAsset") { f -> code(f) == "receiveCoinAsset" },
        DomainCardSpec("sports.walk", "步数与路线", "", DomainCardType.SWITCH, MedusaColors.Blue) { f ->
            code(f) in setOf(
                "walk", "PathThemeMapList", "walkMinimumCompleteCount", "walkPaceMs",
                "syncStepCount", "earliestSyncStepTime"
            )
        },
        DomainCardSpec("sports.donate", "行走捐", "", DomainCardType.SWITCH, MedusaColors.Green) { f ->
            code(f) in setOf("minExchangeCount", "latestExchangeTime")
        },
        DomainCardSpec("sports.tiyu", "文体中心", "", DomainCardType.SWITCH, MedusaColors.Gold) { f ->
            code(f) == "tiyubiz"
        },
        DomainCardSpec("sports.club", "俱乐部", "", DomainCardType.SWITCH, MedusaColors.Purple) { f ->
            code(f) in setOf(
                "club", "clubTrainItemType", "clubTradeMemberType", "clubTradeMemberList"
            )
        },
        DomainCardSpec("sports.health", "健康岛", "", DomainCardType.SWITCH, MedusaColors.GreenDeep) { f ->
            code(f) in setOf(
                "neverLand", "QUERY_SIGN", "QUERY_TASK_CENTER", "QUERY_BUBBLE_TASK",
                "WALK_GRID", "WALK_GRID_MAX", "WALK_GRID_LIMIT", "MapListSwitch",
                "awardspecialActivityReceive"
            )
        }
    )

    private val goldenCards = listOf(
        DomainCardSpec("golden.settings", "本域设置", "启用 / 更多设置", DomainCardType.ENTRY, MedusaColors.GreenDeep,
            switchCode = "enable", match = { code(it) == "enable" }),
        DomainCardSpec("golden.overview", "金豆概览", "金豆罐 / 签到 / 任务", DomainCardType.ACTION, MedusaColors.Gold) { false },
        DomainCardSpec("golden.sign", "每日签到", "", DomainCardType.SWITCH, MedusaColors.Purple,
            switchCode = "goldenBeansSign") { f -> code(f) == "goldenBeansSign" },
        DomainCardSpec("golden.task", "金豆任务", "", DomainCardType.TASK, MedusaColors.Green,
            switchCode = "goldenBeansAutoTask", match = { f ->
                code(f) in setOf("goldenBeansAutoTask", "AutoGoldenBeansTaskList", "GoldenBeansTaskList")
            }),
        DomainCardSpec("golden.sprout", "待领金豆", "待领金豆与奖励", DomainCardType.SWITCH, MedusaColors.Gold,
            switchCode = "goldenBeansVisitGarden") { f -> code(f) == "goldenBeansVisitGarden" },
        DomainCardSpec("golden.miner", "金猫矿工", "", DomainCardType.SWITCH, MedusaColors.Blue) { f ->
            code(f) in setOf("goldenBeansAutoMine", "goldenBeansCollectReward")
        },
        DomainCardSpec("golden.game", "金豆乐园", "", DomainCardType.SWITCH, MedusaColors.Purple,
            switchCode = "goldenBeansGameDraw") { f -> code(f) == "goldenBeansGameDraw" },
        DomainCardSpec("golden.exchange", "肥料/芝麻粒换豆", "", DomainCardType.SWITCH, MedusaColors.Green) { f ->
            code(f) in setOf(
                "goldenBeansAutoManureExchange", "goldenBeansManureExchangeLimit",
                "goldenBeansAutoSesameExchange", "goldenBeansSesameExchangeLimit"
            )
        },
        DomainCardSpec("golden.mall", "商城兑换", "", DomainCardType.TASK, MedusaColors.Gold,
            switchCode = "goldenBeansMallExchange", match = { f ->
                code(f) in setOf("goldenBeansMallExchange", "GoldenBeansMallItemList")
            })
    )

    private val specs = listOf(
        DomainSpec("forest", "蚂蚁森林", ModelGroup.FOREST, forestCards,
            primaryModelCode = "AntForestV2", exclude = { code(it).startsWith("ecoLife") }),
        DomainSpec("greenAction", "绿色行动", ModelGroup.FOREST, greenActionCards,
            onlyMatched = true, primaryModelCode = "GreenFinance"),
        DomainSpec("protect", "生态保护", ModelGroup.PROTECT, protectCards,
            onlyMatched = true, primaryModelCode = "ProtectEcology"),
        DomainSpec("ancient", "古树", ModelGroup.PROTECT, ancientCards,
            onlyMatched = true, primaryModelCode = "ProtectEcology"),
        DomainSpec("ocean", "神奇海洋", ModelGroup.OCEAN, oceanCards,
            onlyMatched = true, primaryModelCode = "AntOcean"),
        DomainSpec("farm", "蚂蚁庄园", ModelGroup.FARM, farmCards,
            onlyMatched = true, primaryModelCode = "AntFarm"),
        DomainSpec("orchard", "芭芭农场", ModelGroup.ORCHARD, orchardCards,
            onlyMatched = true, primaryModelCode = "AntOrchard"),
        DomainSpec("species", "神奇物种", ModelGroup.SPECIES, speciesCards,
            onlyMatched = true, primaryModelCode = "AntDodo"),
        DomainSpec("sports", "医疗健康·运动", ModelGroup.SPORTS, sportsCards,
            onlyMatched = true, primaryModelCode = "AntSports"),
        DomainSpec("golden", "金豆夺宝", ModelGroup.GOLDENBEANS, goldenCards,
            onlyMatched = true, primaryModelCode = "goldenbeans")
    )

    init {
        // 身份以 DomainCatalog 为准；这里只做漂移告警，不改变域页行为。
        val mismatched = specs.mapNotNull { spec ->
            val entry = DomainCatalog.byKey(spec.key)
            when {
                entry == null -> spec.key
                entry.title != spec.title -> spec.key
                entry.groupCode != spec.group.name -> spec.key
                else -> null
            }
        }
        if (mismatched.isNotEmpty()) {
            Log.debug("DomainCatalog 与 DomainRegistry 不一致: $mismatched")
        }
    }

    fun get(key: String?): DomainSpec? = specs.firstOrNull { it.key == key }

    /** 未单独手写的域：按字段名里的「前缀 |」自动分组，生成和森林域一样的卡片 */
    fun generic(key: String, title: String, group: ModelGroup): DomainSpec {
        val all = Model.getGroupModelConfig(group).values
            .flatMap { it.fields.values }
        val groups = LinkedHashMap<String, MutableList<ModelField<*>>>()
        all.forEach { field ->
            val name = field.name ?: ""
            val prefix = name.substringBefore('|').substringBefore('｜').trim()
            if (prefix.isNotBlank() && prefix != name.trim()) {
                groups.getOrPut(prefix) { ArrayList() }.add(field)
            }
        }
        val accents = listOf(
            MedusaColors.Gold, MedusaColors.Blue, MedusaColors.Purple,
            MedusaColors.Green, MedusaColors.GreenDeep
        )
        val cards = ArrayList<DomainCardSpec>()
        cards.add(
            DomainCardSpec(
                "generic.settings", "$title 设置", "运行 / 节奏 / 数据",
                DomainCardType.ENTRY, MedusaColors.GreenDeep
            ) { false }
        )
        // 古树并入生态保护：作为本域的一张子卡，不再占首页域卡
        if (key == "protect") {
            val ancient = all.filter { (it.code ?: "").startsWith("ancientTree") }
            if (ancient.isNotEmpty()) {
                cards.add(
                    DomainCardSpec(
                        "protect.ancient", "古树", "仅周一三五 / 区划代码",
                        DomainCardType.SWITCH, MedusaColors.Gold
                    ) { field -> (field.code ?: "").startsWith("ancientTree") }
                )
            }
        }
        groups.forEach { (prefix, fields) ->
            val codes = fields.mapNotNull { it.code }.toSet()
            cards.add(
                DomainCardSpec(
                    id = "generic.$prefix",
                    title = prefix,
                    subtitle = "${fields.size} 项设置",
                    type = if (fields.any { it is BooleanModelField }) DomainCardType.SWITCH else DomainCardType.ENTRY,
                    accent = accents[cards.size % accents.size],
                    match = { it.code in codes }
                )
            )
        }
        return DomainSpec(key, title, group, cards)
    }
}
