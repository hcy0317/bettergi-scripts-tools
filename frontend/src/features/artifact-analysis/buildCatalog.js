export const artifactSlotOptions = Object.freeze([
  ['flower', '生之花'],
  ['plume', '死之羽'],
  ['sands', '时之沙'],
  ['goblet', '空之杯'],
  ['circlet', '理之冠'],
])

export const artifactStatOptions = Object.freeze([
  ['hp', '生命值'], ['atk', '攻击力'], ['def', '防御力'],
  ['hp_', '生命值%'], ['atk_', '攻击力%'], ['def_', '防御力%'],
  ['eleMas', '元素精通'], ['enerRech_', '元素充能效率'],
  ['critRate_', '暴击率'], ['critDMG_', '暴击伤害'], ['heal_', '治疗加成'],
  ['anemo_dmg_', '风元素伤害加成'], ['cryo_dmg_', '冰元素伤害加成'],
  ['dendro_dmg_', '草元素伤害加成'], ['electro_dmg_', '雷元素伤害加成'],
  ['geo_dmg_', '岩元素伤害加成'], ['hydro_dmg_', '水元素伤害加成'],
  ['physical_dmg_', '物理伤害加成'], ['pyro_dmg_', '火元素伤害加成'],
])

export const artifactSubstatOptions = Object.freeze(
  artifactStatOptions.filter(([key]) => [
    'hp', 'atk', 'def', 'hp_', 'atk_', 'def_', 'eleMas',
    'enerRech_', 'critRate_', 'critDMG_',
  ].includes(key))
)

export const artifactMainStatsBySlot = Object.freeze({
  flower: Object.freeze(['hp']),
  plume: Object.freeze(['atk']),
  sands: Object.freeze(['hp_', 'atk_', 'def_', 'eleMas', 'enerRech_']),
  goblet: Object.freeze([
    'hp_', 'atk_', 'def_', 'eleMas', 'anemo_dmg_', 'cryo_dmg_', 'dendro_dmg_',
    'electro_dmg_', 'geo_dmg_', 'hydro_dmg_', 'physical_dmg_', 'pyro_dmg_',
  ]),
  circlet: Object.freeze([
    'hp_', 'atk_', 'def_', 'eleMas', 'critRate_', 'critDMG_', 'heal_',
  ]),
})

export const artifactSetLabels = Object.freeze({
  archaic_petra: '悠古的磐岩', berserker: '战狂', blizzard_strayer: '冰风迷途的勇士',
  bloodstained_chivalry: '染血的骑士道', brave_heart: '勇士之心',
  crimson_witch_of_flames: '炽烈的炎之魔女', deepwood_memories: '深林的记忆',
  defenders_will: '守护之心', desert_pavilion_chronicle: '沙上楼阁史话',
  echoes_of_an_offering: '来歆余响', emblem_of_severed_fate: '绝缘之旗印',
  flower_of_paradise_lost: '乐园遗落之花', gambler: '赌徒', gilded_dreams: '饰金之梦',
  gladiators_finale: '角斗士的终幕礼', heart_of_depth: '沉沦之心',
  husk_of_opulent_dreams: '华馆梦醒形骸记', instructor: '教官', lavawalker: '渡过烈火的贤人',
  maiden_beloved: '被怜爱的少女', martial_artist: '武人', noblesse_oblige: '昔日宗室之仪',
  ocean_hued_clam: '海染砗磲', pale_flame: '苍白之火', prayers_for_destiny: '祭水之人',
  prayers_for_illumination: '祭火之人', prayers_for_wisdom: '祭雷之人',
  prayers_to_springtime: '祭冰之人', resolution_of_sojourner: '行者之心',
  retracing_bolide: '逆飞的流星', scholar: '学士', shimenawas_reminiscence: '追忆之注连',
  tenacity_of_the_millelith: '千岩牢固', the_exile: '流放者', thundering_fury: '如雷的盛怒',
  thundersoother: '平息鸣雷的尊者', tiny_miracle: '奇迹', vermillion_hereafter: '辰砂往生录',
  viridescent_venerer: '翠绿之影', wanderers_troupe: '流浪大地的乐团', nymphs_dream: '水仙之梦',
  vourukashas_glow: '花海甘露之光', golden_troupe: '黄金剧团', marechaussee_hunter: '逐影猎人',
  song_of_days_past: '昔时之歌', nighttime_whispers_in_the_echoing_woods: '回声之林夜话',
  adventurer: '冒险家', lucky_dog: '幸运儿', traveling_doctor: '游医',
  fragment_of_harmonic_whimsy: '谐律异想断章', unfinished_reverie: '未竟的遐思',
  scroll_of_the_hero_of_cinder_city: '烬城勇者绘卷', obsidian_codex: '黑曜秘典',
  long_nights_oath: '长夜之誓', finale_of_the_deep_galleries: '深廊终曲',
  night_of_the_skys_unveiling: '穹境示现之夜', silken_moons_serenade: '纺月的夜歌',
  aubade_of_morningstar_and_moon: '晨星与月的晓歌', a_day_carved_from_rising_winds: '风起之日',
  celestial_gift: '天之美赐', disenchantment_in_deep_shadow: '影中沉凝的幻灭',
  heart_of_the_furnace: '炉火融炼之心', scarlet_proof: '血红之证',
})

export const artifactCharacterLabels = Object.freeze({
  Aino: '爱诺', Albedo: '阿贝多', Alhaitham: '艾尔海森', Aloy: '埃洛伊', Alyosha: '阿罗夏',
  Amber: '安柏', AratakiItto: '荒泷一斗', Arlecchino: '阿蕾奇诺', Baizhu: '白术', Barbara: '芭芭拉',
  Beidou: '北斗', Bennett: '班尼特', Candace: '坎蒂丝', Charlotte: '夏洛蒂', Chasca: '恰斯卡',
  Chevreuse: '夏沃蕾', Chiori: '千织', Chongyun: '重云', Citlali: '茜特菈莉', Clorinde: '克洛琳德',
  Collei: '柯莱', Columbina: '哥伦比娅', Cyno: '赛诺', Dahlia: '塔利雅', Dehya: '迪希雅',
  Diluc: '迪卢克', Diona: '迪奥娜', Dori: '多莉', Durin: '杜林', Emilie: '艾梅莉埃',
  Escoffier: '爱可菲', Eula: '优菈', Faruzan: '珐露珊', Fischl: '菲谢尔', Flins: '菲林斯',
  Freminet: '菲米尼', Furina: '芙宁娜', Gaming: '嘉明', Ganyu: '甘雨', Gorou: '五郎',
  HuTao: '胡桃', Iansan: '伊安珊', Ifa: '伊法', Illuga: '叶洛亚', Ineffa: '伊涅芙',
  Jahoda: '雅珂达', Jean: '琴', Kachina: '卡齐娜', KaedeharaKazuha: '枫原万叶', Kaeya: '凯亚',
  KamisatoAyaka: '神里绫华', KamisatoAyato: '神里绫人', Kaveh: '卡维', Keqing: '刻晴', Kinich: '基尼奇',
  Kirara: '绮良良', Klee: '可莉', KujouSara: '九条裟罗', KukiShinobu: '久岐忍', LanYan: '蓝砚',
  Lauma: '菈乌玛', Layla: '莱依拉', Linnea: '莉奈娅', Lisa: '丽莎', Lohen: '洛恩',
  Lynette: '琳妮特', Lyney: '林尼', Mavuika: '玛薇卡', Mika: '米卡', Mona: '莫娜',
  Mualani: '玛拉妮', Nahida: '纳西妲', Navia: '娜维娅', Nefer: '奈芙尔', Neuvillette: '那维莱特',
  Nicole: '尼可', Nilou: '妮露', Ningguang: '凝光', Noelle: '诺艾尔', Odette: '奥黛塔',
  Ororon: '欧洛伦', Prune: '布伦妮', Qiqi: '七七', RaidenShogun: '雷电将军', Razor: '雷泽',
  Rosaria: '罗莎莉亚', Sandrone: '桑多涅', SangonomiyaKokomi: '珊瑚宫心海', Sayu: '早柚', Sethos: '赛索斯',
  Shenhe: '申鹤', ShikanoinHeizou: '鹿野院平藏', Sigewinne: '希格雯', Skirk: '丝柯克', Sucrose: '砂糖',
  Tartaglia: '达达利亚', Thoma: '托马', Tighnari: '提纳里', Varesa: '瓦雷莎', Varka: '法尔伽',
  Venti: '温迪', Wanderer: '流浪者', Wriothesley: '莱欧斯利', Xiangling: '香菱', Xianyun: '闲云',
  Xiao: '魈', Xilonen: '希诺宁', Xingqiu: '行秋', Xinyan: '辛焱', YaeMiko: '八重神子',
  Yanfei: '烟绯', Yaoyao: '瑶瑶', Yelan: '夜兰', Yoimiya: '宵宫', YumemizukiMizuki: '梦见月瑞希',
  YunJin: '云堇', Zhongli: '钟离', Zibai: '兹白',
})

export const artifactCharacterAvatarAliases = Object.freeze({
  AratakiItto: 'Itto', KaedeharaKazuha: 'Kazuha', KamisatoAyaka: 'Ayaka',
  HuTao: 'Hutao', KamisatoAyato: 'Ayato', KujouSara: 'Sara', KukiShinobu: 'Shinobu',
  RaidenShogun: 'Shougun', SangonomiyaKokomi: 'Kokomi', ShikanoinHeizou: 'Heizo',
  Thoma: 'Tohma', YaeMiko: 'Yae', Yanfei: 'Feiyan', YumemizukiMizuki: 'Mizuki',
})

export const artifactSetTwoPieceEffectGroups = Object.freeze([
  {label: '冰元素伤害加成 +15%', setKeys: ['BlizzardStrayer', 'FinaleOfTheDeepGalleries']},
  {label: '物理伤害加成 +25%', setKeys: ['BloodstainedChivalry', 'PaleFlame']},
  {label: '攻击力 +18%', setKeys: [
    'BraveHeart', 'EchoesOfAnOffering', 'GladiatorsFinale', 'ResolutionOfSojourner',
    'ShimenawasReminiscence', 'VermillionHereafter', 'NighttimeWhispersInTheEchoingWoods',
    'FragmentOfHarmonicWhimsy', 'UnfinishedReverie', 'ADayCarvedFromRisingWinds',
    'DisenchantmentInDeepShadow', 'HeartOfTheFurnace', 'ScarletProof',
  ]},
  {label: '防御力 +30%', setKeys: ['DefendersWill', 'HuskOfOpulentDreams']},
  {label: '风元素伤害加成 +15%', setKeys: ['DesertPavilionChronicle', 'ViridescentVenerer']},
  {label: '元素充能效率 +20%', setKeys: [
    'EmblemOfSeveredFate', 'Scholar', 'TheExile', 'SilkenMoonsSerenade', 'CelestialGift',
  ]},
  {label: '元素精通 +80', setKeys: [
    'FlowerOfParadiseLost', 'GildedDreams', 'Instructor', 'WanderersTroupe',
    'NightOfTheSkysUnveiling', 'AubadeOfMorningstarAndMoon',
  ]},
  {label: '元素战技伤害 +20%', setKeys: ['Gambler', 'GoldenTroupe']},
  {label: '水元素伤害加成 +15%', setKeys: ['HeartOfDepth', 'NymphsDream']},
  {label: '治疗加成 +15%', setKeys: ['MaidenBeloved', 'OceanHuedClam', 'SongOfDaysPast']},
  {label: '普通攻击与重击伤害 +15%', setKeys: ['MartialArtist', 'MarechausseeHunter']},
  {label: '生命值 +20%', setKeys: ['TenacityOfTheMillelith', 'VourukashasGlow']},
])
