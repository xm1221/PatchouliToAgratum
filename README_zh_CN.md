# Patchouli to Ageratum（`pta`）

[English](README.md) | 简体中文

把 **Patchouli 手册**搬进**藿香（Ageratum）手册**。

首要服务对象是 Hex Casting（HexMod）的藿香手册，但转换器本身是通用的：任何 Patchouli
手册都能镜像，换书只是改一行配置。产物是一个**独立附属 mod**，与 HexMod 永久解耦——
不修改 HexMod 源码、不依赖它的构建，也不在 classpath 上引用它（装了就有那本书，没装就没有）。

* MC **1.21.1** / NeoForge **21.1.236**
* 运行期硬依赖：**Patchouli** `1.21.1-93-NEOFORGE` + **藿香 Ageratum** `0.0.1+build.121`（都是客户端侧）
* mod id `pta` · 包 `cn.xm1221.pta` · 版本 `0.1.0` · MIT

---

## 1. 它做什么

游戏组装资源包时，这个 mod 把配置里点名的 Patchouli 书**读一遍**，转成藿香导读文档，
塞进一个**虚拟资源包**（不写盘、不是数据包、不需要重启资源重载）：

```
Patchouli 书（其它 mod 的 jar 里）
  └─ 分类 / 条目 / 页面 JSON + 该书的语言文件
        │  pta-core：纯 JVM 转换（不碰 MC 类型）
        ▼
  藿香 Markdown 文档 + 转换报告
        │  虚拟资源包（AddPackFindersEvent + 自定义 PackResources）
        ▼
  藿香导读 UI：侧边栏、搜索、正文可选中/可跳转
        └─ 转换不了的页面：<pta:page> 宿主组件里画**真正的 Patchouli 页面**
```

两条设计主线：

* **不复制页型，托管渲染。** Patchouli 的 `BookPage.render(...)` 不依赖 `Screen`，所以能离屏构造
  真实的 `GuiBookEntry` 后在藿香文档里画出来。16 种内置页型、别的 mod 的模板页、
  `IComponentProcessor`、HexMod 的图案页全部原样生效，**零复刻成本**，也不会随版本漂移。
* **能忠实转换的就写成原生 Markdown。** 正文（`patchouli:text` / `link`、`$(...)` 宏、字形）、
  配方、物品展示、图片、实体都转成藿香自己的语法——可选中、可搜索、链接可跳，
  而不是一块块贴图。

## 2. 装与用

装到客户端（`mods/` 里放 `pta`、`patchouli`、`ageratum`），启动即可。

**配置**：`config/pta-common.toml`

| 键 | 默认 | 说明 |
|---|---|---|
| `exclude.mods` | `[]` | 按**模组命名空间**排除，这些模组的书一律不生成。例：`["hexcasting"]` |

凡是找到的 Patchouli 手册都生成——「镜像一本书」本来就是这个意思，所以只剩排除这一个开关。
这份配置是 **common** 而不是客户端侧，是有意的：同一份名单既决定生成哪些导读，也决定创造模式
里有哪些指南书物品，而物品是**两侧都要注册**的。（按世界存档的 server 配置做不到这点：
物品注册时它还不存在。）

> 被排除掉的书会记一条日志，方便排查「这本书怎么没有」。
> 改配置**下次启动**生效：导读是在游戏组装资源包时生成的。
> 找书是在模组自己的文件里找：`data/<命名空间>/patchouli_books/<书id>/`。之所以直接读文件，
> 是因为导读是在游戏组装资源包的过程中生成的——那时 Patchouli 自己的书单还不存在。因此只存在于
> 数据包里的书找不到，以前也找不到：导读是从书**自己的文件**生成的，不是从 Patchouli 已加载的
> 副本里读的。
> `pta:spike` 是本 mod 自带的练手小书，用它在不装任何其它 mod 的情况下验证镜像流程；
> 它和别的书一样会被镜像，不想要就把 `pta` 排除掉。

**物品**：`pta:guidebook`（创造页「工具与实用物品」里每本镜像书一个）。

* 带 `pta:guide` 组件（指向 `namespace:book`）→ 名字是那本书的名字、贴图是那本书手册的贴图、右键开对应导读
* **没有组件** → 就是一本藿香指南书本身：名字与贴图都是藿香的，右键不做事

**命令**（都是客户端命令）：

| 命令 | 作用 |
|---|---|
| `/ageratum <namespace>` | 藿香自己的命令，打开 `namespace:index` 这本书 |
| `/pta export all [目录]` | 把**所有**镜像书的生成结果按资源包布局落盘（默认目录 `pta-export`） |
| `/pta export <namespace:book> [目录]` | 只导出某一本 |

导出的目录里有 `assets/<ns>/ageratum/<lang>/*.md`、`pack.mcmeta`（直接就是一个可加载的资源包），
每本书另有一份 `<ns>/<book>-conversion-report.md`：转换了多少页、哪些页走了托管、
哪些 key 缺失、哪些地方做了有损转换。

## 3. 页型映射

| Patchouli 页 | 变成 |
|---|---|
| `patchouli:text` / `link` | 原生 Markdown（保留 `$(...)` 宏、字形、`$(l:…)` 链接改写为 md 链接） |
| `crafting` / `smelting` / `blasting` / `smoking` / `campfire` / `smithing` / `stonecutting` | `<recipe id="…"/>`（藿香原生配方组件）+ 文案走 Markdown |
| `hexcasting:crafting_multi` | 每个变体一张 `<recipe>`，用 `<row>` 自动折行（牺牲原书的「合并投料」显示） |
| `hexcasting:brainsweep` | 读配方文件自己重建：**一张居中 row**（怪 → 方块 → 媒质 → 产物）+ 文案走 Markdown |
| `spotlight` | `<row>` 里一个 `<item id="…"/>` + 文案 |
| `image` | 原生 Markdown 图片 |
| `entity` | `<entity id="…"/>` + 文案 |
| `empty` | 什么都不输出 |
| HexCasting 图案页（`hexcasting:pattern` / `manual_pattern` / `manual_pattern_nosig`） | 标题、Input/Output、正文转原生 Markdown；六边形由 `<pta:pattern>` 画（版式：标题在上、图案居中、IO 在图案正下方、正文在后）——页自带 `patterns` 的（那些没有自己形状的操作：数字之精思、向量常量、掩码）就照页上写的画，图案才不会丢 |
| 其余（含别的 mod 的模板页） | `<pta:page book="…" entry="…" page="…"/>` —— 在藿香文档里画**真实的那一页** |

brainsweep 是藿香唯一画不了的配方：它不是原版配方，`<recipe>` 没有对应工厂，所以按页面点名的
配方文件重建。Patchouli 围在它外面的那张框贴图**没有复刻**——框里只有槽位与箭头、没有物品画，
脱开内容单独画也没有对齐关系；改成一行居中的活组件，怪、方块、媒质都能悬停看名字，也可搜索。
配方文件读不明确的一律不猜（tag 形式的方块或怪、少见的 ingredient、除不尽任何紫水晶单位的媒质），
那页仍旧回退托管。

**锁定**：Patchouli 条目的 `advancement`、以及「整章都被锁」的章节，转成
`<pta:locked advancements="…" names="…" [unlock="any"] [secret="true"]>…</pta:locked>`。
是否解锁是**每个玩家、每个时刻**的事，而藿香把文档缓存在静态表里，所以在资源包里按玩家给不同正文
是死路——文档只写「要求」，由客户端组件**每帧绘制时**判定（复用 Patchouli 自己的
`ClientAdvancements.hasDone`），达成后当场打开、不需要重载。提示框说条件时用**成就名称**
（优先客户端实时的名字，取不到才用文档里 `names` 记下的名字，最后才是 id）。

## 4. 已知偏差与限制

* **侧边栏仍会列出被锁（含 `secret`）条目的标题**：标题来自静态目录树，藿香没有锁的概念，
  要藏需要 mixin 它的目录树。`secret` 做到的是「不点名解锁条件」。
* 被门包住的标题不再是文档顶层组件 → 锁住的文档里**锚点失效**（我们本来也不发锚点链接）。
* **嵌套分类被压平**：藿香侧边栏只显示「顶级目录的文档」与「子目录的 index」，子目录自己的文档
  永不显示，所以层级折进文件名（`patterns/great_spells/altiora` → `patterns/great_spells__altiora`），
  子目录的 index 也压平成上级目录里的普通文档，靠 front matter 的 `weight` 排到前面。
* **不写 `items:` front matter**：条目图标不绑定物品，手册不自增长原书没有的行为。
* `hexcasting:crafting_multi` 的「合并投料」显示被逐条配方取代（差异会记进报告）。
* 书单变化后，已有的物品 stack 上的组件不会跟着变（有意为之）。
* 导出是**同步**写几百个文件（为了聊天栏回显顺序确定），量大时会卡一下。
* 藿香侧 scissor 换算在 `scale != 1` 时是错的 → 本 mod 不画书面/另一页/控件，因此完全不碰 scissor。

## 5. 构建与开发

```powershell
cd E:\miemod\PatchouliToAgratum
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'   # Gradle 必须跑在 JDK 21
.\gradlew.bat build                 # 会连带跑核心的离线回归（唯一的自动化检查）
.\gradlew.bat :pta-core:test        # 只跑纯 JVM 回归，不需要 MC
.\gradlew.bat runClient             # 起 dev 客户端
```

依赖里若缺东西：`.\gradlew.bat --refresh-dependencies`。

**两个模块，一条纪律**：

* `pta-core/` —— 纯 JVM 转换核心：书模型、JSON5 与 `$(...)`/字形的解析、页型策略、藿香 Markdown 输出。
  **不允许引用任何 MC / Patchouli / 藿香类型**（它编译时没有它们的 classpath），所以能离线单测、
  也能整块复用到别处。
* 根项目 —— mod 侧胶水：入口、配置、虚拟资源包、三个藿香扩展组件、物品、命令、模型覆盖。
  核心的源码是**直接编进主 source set** 的（`sourceSets.main.java.srcDir(project(':pta-core')…)`），
  不是 `implementation project(':pta-core')`：MDG 的 dev 运行时 classpath 只认声明的 source set
  与一份库清单，普通项目依赖编译得过、运行时 `ClassNotFoundException`。核心的「纯」由构建本身
  担保——同一份源码在没有 MC 的 classpath 下编译并跑测试，`build` 依赖它。

```
pta-core/src/main/java/cn/xm1221/pta/core/
├─ book/            BookLayout（读分类/条目/锁）· BookConverter（出文档）· BookSource · 文本转换
│  └─ page/         PageTypeRegistry + 每种页型一个 Renderer（兜底放最后）
├─ lang/Json5       语言文件解析与 flatten
├─ text/            Patchouli 文本扫描、宏展开、藿香 Markdown 书写
└─ report/          ConversionReport（覆盖统计与有损项）

src/main/java/cn/xm1221/pta/
├─ PtaMod · PtaConfig · PtaBookList · PtaGuides
├─ PtaComponents（藿香扩展组件注册）· PtaPageRenderers（页型策略接线）
├─ PtaDataComponents（pta:guide）· PtaItems · item/GuideBookItem
└─ client/
   ├─ PtaClient（资源包 + 命令 + 模型事件）
   ├─ component/   MDPatchouliPageComponent · MDHexPatternComponent · MDLockedComponent
   ├─ lock/PtaLocks · render/（Patchouli 页宿主 + 图案反射桥）· model/（按组件换物品模型）
   ├─ source/（ModFileBookSource · PtaGuideDocuments · PtaGuidePack · PtaPackFinder）
   └─ export/ · command/
```

写代码前值得知道的几个坑（都已在代码注释里就地说明）：藿香 `<row>` 会把子块宽度收成**首选宽度**
（靠 `maxX()` 自居中的图案/图片会左对齐）、组件的**位置靠 pose 不靠偏移**、
1.21.1 与 1.21.4 的 scissor 语义相反、语言文件里嵌套节点是「自己带分隔符」的写法
（`"advancement.hexcasting:"`、`"lore/"`）。

## 6. 移植 / 衍生

想换 MC 版本、换加载器、换目标手册 UI，或者只想镜像自己那本书——
各条路的工作量、要改哪些文件、哪些坑要重踩一遍，见 **[docs/PORTING.md](docs/PORTING.md)**。

## 7. 许可

MIT，见 [LICENSE](LICENSE)。`TEMPLATE_LICENSE.txt` 是 NeoForge MDK 模板原本的许可说明
（它本身就是 MIT，所以不冲突）；映射名（Mojang 官方映射）另受其自身许可约束，见
<https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>。
