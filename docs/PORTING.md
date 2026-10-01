# 移植指南：怎么把这个项目搬走最省力

先说结论，四条路的成本差着量级：

| 想做的事 | 要动的代码 | 粗估 |
|---|---|---|
| **D. 镜像另一本 Patchouli 书** | 0 行（改配置），页型不够时补一个 Renderer | 分钟级 / 半天 |
| **A. 换 MC 版本**（藿香在该版本存在） | 版本号 + 3 个适配面里的报错 | 2–4 天 |
| **C. 换目标手册 UI**（不是藿香） | 一个 writer + 三件特殊件怎么表达 | 3–5 天（Markdown 家族）／更久（自绘 UI） |
| **B. 换加载器**（Fabric 等） | 入口、事件、配置、资源包、模型、网络同步 | 1–2 周 |
| **E. 做成「不需要 mod 的纯资源包」** | 放弃托管渲染/图案/锁，降级成静态内容 | 1–3 天（能用，能力降级） |

真正的成本不在改代码，在**实机重新核对**——这个项目里有六件事是靠读字节码 + 实机对照才定下来的
（见文末清单），换任何环境都要重来一遍。

---

## 0. 先认清这条边界：藿香（Ageratum）是硬前提

本 mod 不自己画界面，它把内容**喂给藿香**，并在藿香里挂三个自己的扩展组件
（`<pta:page>` / `<pta:pattern>` / `<pta:locked>`）。所以：

* **目标 MC 版本必须有藿香可用**。藿香目前只发 NeoForge 构建（本地 Gradle 缓存里也只有
  `ageratum-neoforge-1.21.1`）。没有藿香的版本，只能走 C 或 E 两条路。
* `Patchouli` 也是硬依赖且**必须是真的 Patchouli 运行时**：D1 的核心手法是把真实的
  `GuiBookEntry` 离屏构造出来再画，不是自己解析 JSON 画一遍。

## 1. 已经为移植做对的地方（别推翻）

| 设计 | 为什么它让移植变便宜 |
|---|---|
| **D1 托管渲染**：转换不了的页交给真 Patchouli 画 | 页型数量不再乘进移植成本。复刻 16 种页型 × 每个新版本 = 无底洞。 |
| **D6 纯 JVM 核心 `pta-core`** | 25 个文件、**零个非 `java.*` import**（可核对）：书模型、JSON5、`$(...)` 宏、页型策略、输出全在里面，不带 MC/Patchouli/藿香类型，能离线编译、单测、复用。 |
| **D5 与 HexMod 零依赖** | 换环境影响不到它：HexMod 的自定义组件是 `patchouli:custom` 里写类名反射加载的，装了就在 classpath，没装就没有那本书。 |
| **D3 虚拟资源包** | 只依赖「资源包注入」这一个概念，而不是藿香内部某个类的 Mixin（曾经走过 mixin `GuideDocumentCache` 的路，一次只能喂给「解析」那次调用，命令就报 `Guide file not found`）。 |
| **D16/D17 锁在运行时判定** | 换个版本不用重新设计「按玩家给内容」这件事——那件事在藿香里根本做不到（文档缓存在静态表，只在资源重载时重建）。 |
| **单一物品 + 数据组件** | 不为每本书注册物品；换版本时物品注册只有一个入口。 |

## 2. 版本敏感面：到底哪些文件会找你麻烦

mod 侧 24 个 Java 文件里，按依赖统计：

| 依赖 | 文件数 | 具体是哪些 |
|---|---|---|
| 藿香 API | **5** | `PtaComponents`（扩展组件注册）、`PtaGuides`（`Ageratum.openGuide`）、`MDHexPatternComponent`、`MDLockedComponent`、`MDPatchouliPageComponent` |
| Patchouli API | **4** | `MDPatchouliPageComponent`（页宿主）、`PtaLocks`（`ClientAdvancements.hasDone` / `Advancement.name`）、`PatchouliPageHost`（构造 `GuiBookEntry`）、`HexPatternBridge`（反射 HexMod 的图案组件） |
| NeoForge API | 11 | 入口、配置、资源包注入、数据组件、物品、命令、模型事件 |
| 原生 Minecraft | 19 | 剩下的都在 |

`pta-core` 里还有约 12 个文件**生成藿香专属语法**（`text/AgeratumTextWriter` + `book/page/*Renderer`
十个左右 + `BookConverter` 里的 front matter 与 `<pta:locked>`）。这是「换目标 UI」时要重写的那一半，
也是「换 MC 版本」时**不用动**的那一半。

### 换 MC 版本（轴 A）步骤

1. `gradle.properties` 改 `minecraft_version` / `neo_version` / `patchouli_version` / `ageratum_version`
   与各自的 `*_version_range`，然后 `.\gradlew.bat --refresh-dependencies`。
2. 编译报错基本集中在这几处，按顺序处理：
   * `PtaComponents`：藿香注册扩展组件的方式（`EXTENSION_COMPONENTS.register(id, factory)`）；
   * 三个组件的 `render` / `getHeight` / `getPreferredWidth` / `MDRenderContext.child(...)` 签名；
   * `PtaLocks`：`ClientAdvancements.hasDone` 与取成就名的 API；
   * `GuideBookModel` / `PtaClientModels`：`ModelEvent.RegisterAdditional`、`ModifyBakingResult`、
     `ItemOverrides`、`BakedModel` 的成员；
   * `PtaGuidePack` / `PtaPackFinder`：`PackResources` 与 `RepositorySource`。
     **这个接口在版本之间改过好几次，是最容易炸的文件**，而且它炸的方式是运行时报
     `Guide file not found`（因为每个查询都走 `ResourceManager`），不是编译错误。
3. 数据组件（1.20.5+ 才有）不存在时，`pta:guide`（`PtaDataComponents`）要换成 NBT 或子类物品；
   `networkSynchronized` 是 NeoForge 概念，也要另找办法。
4. 走一遍文末的实机清单。

**别做的事**：为了少踩 API 的坑而把 `<pta:page>` 换成自己复刻页面。复刻成本随
「页型数 × 版本数」增长，而托管渲染一次也不用重做。

### 换加载器（轴 B）

前提是藿香有对应加载器的构建。要补的东西（NeoForge → Fabric 为例）：

* 入口：`PtaMod` / `PtaClient`（`@Mod`、`RegisterClientCommandsEvent`）→ Fabric 的
  `ModInitializer` / `ClientModInitializer`；
* 资源包注入：`AddPackFindersEvent` → `ResourceManagerHelper.registerBuiltinResourcePack` /
  自定义 `ResourcePackProvider`；
* 配置：`ModConfigSpec`（`PtaConfig`）→ 自写 JSON/TOML 或 cloth-config；
* 物品与数据组件：`DeferredRegister` → `Registry.register`；数据组件的 `persistent` /
  `networkSynchronized` 语义要重写（Fabric 上通常得自己同步或干脆不入 NBT）；
* 模型覆盖：`ModelEvent` → `ModelLoadingPlugin` + `ItemModelManager`（两边 API 完全不同，
  这是工作量最大的一块）；
* 物品栏插入：`BuildCreativeModeTabContentsEvent` → `ItemGroupEvents`。

**省力的做法**：把这堆集中到一个 `PtaPlatform` 接口后面（见 §3），mod 侧代码只认接口。
不做这层抽象也能移植，但下次再换加载器就得把这些文件重读一遍。

### 换目标 UI（轴 C）

`pta-core` 输出的是一份「文档模型」+ 文本，最后落成藿香的 Markdown。换目标要动的是**输出端**：

1. 写一个新的 writer（`text/AgeratumTextWriter` 是具体类，现在是直接继承来改写）；
2. 决定三件特殊件在目标 UI 里怎么表达——这是真正的工作量所在：
   * **托管页**：目标 UI 能不能画任意组件 / 有没有插件式组件？不能就只能降级成文本或截图，或者干脆复刻页型。
   * **图案六边形**：HexMod 的图案是纯矢量绘制，只要目标 UI 允许自定义组件就能画（我们只反射调它的
     `build` / `render`，不依赖它的类）。
   * **锁**：目标 UI 有没有「每帧判定」的组件模型？没有的话锁只能退化成「生成期不输出」，也就是
     失去「达成后不用重载即打开」这个体验。

**最省力的重构（建议先做，见 §4）**：把三个特殊件定义成**与目标无关的中间表示**，让 writer 决定
怎么落地。现在它们是散落在各 Renderer 里的字符串拼接，换目标时得逐个文件找。

### 镜像另一本书（轴 D）

**这条已经零成本**：把书放进任何模组的 `data/<命名空间>/patchouli_books/<书id>/`，
启动就会被发现并镜像（`config/pta-common.toml` 的 `exclude.mods` 是唯一的减法）。
缺什么会写在导出报告里：

* 页面文字缺 lang key → 报告里列出来（真书验证过：HexMod 的 557 个 page key 逐字无损）；
* 遇到没有 Renderer 的页型 → 走托管，报告里能看到托管清单；想转成原生 Markdown 就补一个
  Renderer（`pta-core/src/main/java/.../book/page/` 下每个约 50–150 行，配一个单测，约 0.5–1 天）。

已经用一本「为覆盖全部页型而生」的书验证过泛用性：Patchouli 自带的
`comprehensive_test_book` 全量转换后，托管集合**精确等于**它自带的几个自定义模板页型，
没有任何 `patchouli:text` / 配方页 / `empty` / `link` 掉进托管兜底。

### 做成不需要 mod 的纯资源包（轴 E）

`/pta export` 已经能落盘成一个资源包，但它**不是自足的**：里面的 `<pta:page>` / `<pta:pattern>` /
`<pta:locked>` 需要本 mod 提供的组件，藿香本体没有。要给别人「不装 mod 也能用」的包，得：

* 托管页 → 静态内容（截图或降级文本，会掉可交互性）；
* 图案 → 位图或干脆只留 IO 文本；
* 锁 → 做不到（资源包是静态的，且藿香缓存文档），只能全部解锁。

1–3 天能出，但等于放弃了 D1/D16 两条主要收益。只在「对方绝对不能装 mod」时考虑。

## 3. 为下一次移植做的准备（按性价比排序）

1. **加一个 CLI（`pta-core` 独立可跑）**——0.5–1 天，收益最大。
   现在想验证「换一本书 / 换一段输出语法」必须跑 JUnit 甚至起游戏。加一个
   `main`：输入一个资源目录（或 jar）+ 语言 + 书名，输出 Markdown 目录与转换报告。
   之后所有输出端的移植都能**离线秒级迭代**，也顺便把 S5 里欠的「离线导出/diff/校对」补上。
2. **抽出 `GuideWriter` 接口 + 三件特殊件的 IR 节点**——2–3 天，动约 12 个核心文件。
   收益：换目标 UI = 写一个 writer；换 MC 版本不受影响。
3. **抽出 `PtaPlatform` 接口（打开导读 / 判定成就 / 换物品模型 / 注册组件）**——1–2 天。
   收益：换加载器从「重读 11 个文件」变成「写一个适配器」。藿香相关的 5 个文件收在后面。
4. **黄金文件回归**：把真书（HexMod + Patchouli 测试书）生成的 Markdown 快照进测试，
   每次构建逐字 diff——1 天以内。收益：任何移植只要输出变了就立刻可见，不必靠眼睛比对。

建议顺序：先 1（立刻能用），再 4（锁住现有行为），再 2/3（真要移植时再做，别提前抽象）。

## 4. 换环境后必须重新实机核对的六件事

这六条都是「读字节码才看出来、编译器不会提醒你」的，换版本/换加载器后请逐条过一遍：

1. **宿主页的位置与尺寸**：`PatchouliPageHost` 离屏构造 `GuiBookEntry` 有副作用（构造本身会
   改导航状态），并且我们靠「不给它画书面/另一页/控件」来绕开藿香 scissor 换算的缺陷
   （`scale != 1` 时是错的，且 1.21.1 与 1.21.4 的 `enableScissor` 语义相反）。
2. **图案的纵向偏移**：HexMod 的图案组件是在原点**下方 16px** 画的，我们把 pose 上提 16px、
   文字行 y=66 才对得上。版本一换，这个数字要重新量。
3. **`<row>` 的宽度收缩**：藿香递给子块的是 `min(getPreferredWidth, maxX)`，段落的首选宽度就是整行，
   所以「组件 + 段落」的 row 一定折行；靠 `maxX()` 自居中的图案/图片进了 row 会左对齐。
4. **组件的定位是 pose 不是偏移**：`MDComponent.render` 就在 pose 原点画
   （`drawString(font, line, 0, 9 * index, …)`），`MDRenderContext.offsetX/offsetY` 不参与定位；
   容器必须 `pushPose` → 画 → `popPose` → `translate(0, 高度 + 5, 0)`。只推偏移 = 所有子块叠在一起。
5. **物品模型覆盖**：`RegisterAdditional` + `ModifyBakingResult` + 自己包一层 `ItemOverrides`
   这条路子在目标版本是否还成立（不要退回 BEWLR：在 BEWLR 里调 `ItemRenderer.render` 会回调
   `IClientItemExtensions.getCustomRenderer().renderByItem` 自己，死循环）。
6. **扩展标签语法**：藿香的 `EXTENSION_TAG_OPEN_PATTERN` / `PARAM_PAIR_PATTERN` 只认它那几种写法，
   闭合标签是 `</<id>>` 或 `</<path>>`。我们的属性里没有引号以外的东西，但换版本时值得重新确认一次。

另外两条与版本无关、但移植时容易忘记的：

* **语言文件是扁平的**：1.21.1 的 `Language.loadFromJson` 只吃字符串，嵌套对象不是合法语言文件。
  我们的 `Json5.flatten` 要复刻 Gradle 那套 flatten 规则——嵌套节点是「自己带分隔符」的写法
  （`"advancement.hexcasting:"`、`"lore/"`），父 key 已经以 `:` `.` `/` 结尾时**不要再补一个点**。
* **同一命名空间里的第二本书会互相覆盖**（藿香的文档缓存按 `namespace:path` 取，不含书名）。
  移植时若要镜像同一命名空间的多本书，得给路径加前缀并提示。

## 5. 一句话建议

* 只想**多镜像几本书**：改配置，别动代码。
* 只想**跟上 MC 版本**：先确认藿香在该版本存在，然后按 §2 的顺序改，预留一半时间给 §4 的实机核对。
* 想**换一个手册 UI 或加载器**：先花一周做 §3 的 1、4、2 三件预备工作，之后每次移植都能压到几天，
  而且不必再碰 `pta-core` 的内容转换部分。
