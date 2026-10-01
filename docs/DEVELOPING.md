# 构建与开发

[README](../README.md) 讲功能，这份讲怎么改它。想换 MC 版本、换加载器、换目标手册 UI，或者只想
镜像自己那本书，见 **[PORTING.md](PORTING.md)**。

## 跑起来

```powershell
cd E:\miemod\PatchouliToAgratum
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'   # Gradle 必须跑在 JDK 21
.\gradlew.bat build                 # 会连带跑核心的离线回归（唯一的自动化检查）
.\gradlew.bat :pta-core:test        # 只跑纯 JVM 回归，不需要 MC
.\gradlew.bat runClient             # 起 dev 客户端
```

依赖里若缺东西：`.\gradlew.bat --refresh-dependencies`。

## 两个模块，一条纪律

* `pta-core/` —— 纯 JVM 转换核心：书模型、JSON5 与 `$(...)`/字形的解析、页型策略、藿香 Markdown
  输出、书 ↔ 手册的合成配方 JSON。**不允许引用任何 MC / Patchouli / 藿香类型**（它编译时没有它们的
  classpath），所以能离线单测、也能整块复用到别处。
* 根项目 —— mod 侧胶水：入口、配置、虚拟资源包（资源包给导读、数据包给配方）、三个藿香扩展组件、
  物品、命令、模型覆盖。核心的源码是**直接编进主 source set** 的
  （`sourceSets.main.java.srcDir(project(':pta-core')…)`），不是 `implementation project(':pta-core')`：
  MDG 的 dev 运行时 classpath 只认声明的 source set 与一份库清单，普通项目依赖编译得过、运行时
  `ClassNotFoundException`。核心的「纯」由构建本身担保——同一份源码在没有 MC 的 classpath 下编译
  并跑测试，`build` 依赖它。

## 目录结构

```
pta-core/src/main/java/cn/xm1221/pta/core/
├─ book/            BookLayout（读分类/条目/锁）· BookConverter（出文档）· BookSource · 文本转换
│  ├─ page/         PageTypeRegistry + 每种页型一个 Renderer（兜底放最后）
│  └─ recipe/       GuideRecipes（书 ↔ 手册的配方与解锁进度 JSON）
├─ lang/Json5       语言文件解析与 flatten
├─ text/            Patchouli 文本扫描、宏展开、藿香 Markdown 书写
└─ report/          ConversionReport（覆盖统计与有损项）

src/main/java/cn/xm1221/pta/
├─ PtaMod · PtaConfig · PtaBookList · PtaGuides
├─ PtaComponents（藿香扩展组件注册）· PtaPageRenderers（页型策略接线）
├─ PtaDataComponents（pta:guide）· PtaItems · item/GuideBookItem
├─ recipe/PtaRecipePack（书 ↔ 手册的配方，按服务端数据包发出去）
└─ client/
   ├─ PtaClient（资源包 + 命令 + 模型事件）
   ├─ component/   MDPatchouliPageComponent · MDHexPatternComponent · MDLockedComponent
   ├─ lock/PtaLocks · render/（Patchouli 页宿主 + 图案反射桥）· model/（按组件换物品模型）
   ├─ source/（ModFileBookSource · PtaGuideDocuments · PtaGuidePack · PtaPackFinder）
   └─ export/ · command/
```

## 几个坑

写代码前值得知道的：藿香 `<row>` 会把子块宽度收成**首选宽度**（靠 `maxX()` 自居中的图案/图片会
左对齐）、组件的**位置靠 pose 不靠偏移**、1.21.1 与 1.21.4 的 scissor 语义相反、语言文件里嵌套
节点是「自己带分隔符」的写法（`"advancement.hexcasting:"`、`"lore/"`）、原版 `Ingredient` 匹配不了
组件（多写的 `components` 会被**静默忽略**，要配组件只能用 `neoforge:components`）。每条都在它咬人
的地方有代码注释，PLAN 里还有更长的踩坑记录。
