# Patchouli to Ageratum (`pta`)

English | [简体中文](README_zh_CN.md)

Moves **Patchouli books** into **Ageratum guides**.

Its first target is Hex Casting's own book, but the converter itself is generic: any Patchouli book
can be mirrored, and pointing it at another one is a line of configuration. The result is a
standalone add-on mod, permanently decoupled from Hex Casting — it does not patch Hex Casting, does
not build against it, and does not reference it on the classpath (with the mod installed its book is
there; without it, it is not).

* Minecraft **1.21.1** / NeoForge **21.1.236**
* Required at runtime: **Patchouli** `1.21.1-93-NEOFORGE` and **Ageratum** `0.0.1+build.121` (both client-side)
* mod id `pta` · package `cn.xm1221.pta` · version `0.1.0` · MIT

---

## 1. What it does

While the game assembles its resource packs, the mod **reads** every Patchouli book named in its
config, converts it into Ageratum guide documents, and serves them from a **virtual resource pack**
(nothing is written to disk, it is not a data pack, and no resource reload is needed):

```
Patchouli book (inside some other mod's jar)
  └─ category / entry / page JSON + that book's language files
        │  pta-core: a plain-JVM conversion (no Minecraft types)
        ▼
  Ageratum Markdown documents + a conversion report
        │  virtual resource pack (AddPackFindersEvent + a custom PackResources)
        ▼
  Ageratum guide UI: sidebar, search, selectable and linkable prose
        └─ pages that cannot be converted: drawn as the **real Patchouli page** inside a
           <pta:page> host component
```

Two design lines run through all of it:

* **Do not reimplement page types; host them.** Patchouli's `BookPage.render(...)` does not need a
  `Screen`, so a real `GuiBookEntry` can be built off-screen and painted inside an Ageratum document.
  All 16 built-in page types, other mods' template pages, their `IComponentProcessor`s and Hex
  Casting's pattern pages work as they are: **zero reproduction cost**, and nothing to drift when
  versions change.
* **What can be converted faithfully becomes native Markdown.** Prose (`patchouli:text` / `link`,
  `$(...)` macros, glyphs), recipes, item displays, images and entities all become Ageratum's own
  markup — selectable, searchable, with links that navigate — instead of inert pictures.

## 2. Installing and using it

Install it on the client (put `pta`, `patchouli` and `ageratum` in `mods/`) and start the game.

**Configuration**: `config/pta-client.toml`

| Key | Default | Meaning |
|---|---|---|
| `books.mirror` | `["hexcasting:thehexbook", "pta:spike"]` | books to mirror, as `namespace:book`; `*` means every book that was found |
| `exclude.mods` | `[]` | excluded by **mod namespace**, subtracted from the whitelist. For example `["hexcasting"]` |

> A book on the whitelist that the blacklist excludes is logged and skipped, so "I asked for it and
> nothing happened" is answerable.
> Configuration changes take effect on the **next start**: guides are generated while the game
> assembles its resource packs.
> `pta:spike` is a small practice book that ships with this mod, for exercising the mirroring
> pipeline without installing anything else.

**Item**: `pta:guidebook` (one per mirrored book, in the "Tools & Utilities" creative tab).

* With a `pta:guide` component (pointing at `namespace:book`) — its name is that book's name, its
  texture is that book's guide texture, and using it opens that guide.
* **Without** the component it is an Ageratum guidebook itself: Ageratum's name and texture, and
  using it does nothing.

**Commands** (both client-side):

| Command | Effect |
|---|---|
| `/ageratum <namespace>` | Ageratum's own command; opens `namespace:index` |
| `/pta export all [directory]` | writes **every** mirrored book out as a resource pack layout (default directory `pta-export`) |
| `/pta export <namespace:book> [directory]` | writes one book |

An exported directory holds `assets/<ns>/ageratum/<lang>/*.md` and a `pack.mcmeta` — that is, a
resource pack that loads directly — plus `<ns>/<book>-conversion-report.md` per book: how many pages
were converted, which pages were hosted, which language keys were missing, and where the conversion
had to lose something.

## 3. Page type mapping

| Patchouli page | Becomes |
|---|---|
| `patchouli:text` / `link` | native Markdown (macros, glyphs and `$(l:…)` links rewritten as Markdown links) |
| `crafting` / `smelting` / `blasting` / `smoking` / `campfire` / `smithing` / `stonecutting` | `<recipe id="…"/>` (Ageratum's own recipe component) + prose as Markdown |
| `hexcasting:crafting_multi` | one `<recipe>` per variant, wrapped by a `<row>` (the book's "combined inputs" display is lost) |
| `hexcasting:brainsweep` | the recipe rebuilt from its datapack file as one centred row — mob, block, media cost, result — + prose as Markdown |
| `spotlight` | one `<item id="…"/>` in a `<row>` (or on its own) + prose |
| `image` | native Markdown image |
| `entity` | `<entity id="…"/>` + prose |
| `empty` | nothing at all |
| Hex Casting pattern pages (`hexcasting:pattern` / `manual_pattern` / `manual_pattern_nosig`) | title, Input/Output and prose as native Markdown; the hexagon itself is drawn by `<pta:pattern>` (title on top, hexagon centred, Input/Output right under it, prose last) |
| everything else (including other mods' template pages) | `<pta:page book="…" entry="…" page="…"/>` — **that very page** is drawn inside the Ageratum document |

Brainsweep is the one recipe Ageratum cannot draw for itself: it is not a vanilla recipe, so the
page is rebuilt from the recipe file the page names. The frame texture Hex Casting paints around it
is not reproduced — the mob, the blocks and the media are live components, which can be hovered and
read, where a picture of a frame could not be. A recipe that cannot be read plainly (a block or mob
given by tag, an unusual ingredient, a cost that is not a whole number of amethyst units) leaves the
page hosted instead.

**Locking**: a Patchouli entry's `advancement`, and a chapter whose every entry is locked, become
`<pta:locked advancements="…" names="…" [unlock="any"] [secret="true"]>…</pta:locked>`.
Whether a lock is open is a per-player, per-moment question, while Ageratum caches documents in a
static table — so giving different players different prose in the resource pack is a dead end.
Documents only state the requirement, and a client component decides **while drawing each frame**
(reusing Patchouli's own `ClientAdvancements.hasDone`); once earned, the page opens on the spot and
nothing has to be reloaded. The notice names the condition by **achievement name** — the live
client-side name when it can be asked, otherwise the name recorded in the document's `names`
attribute, and the id only as a last resort.

## 4. Known deviations and limits

* **The sidebar still lists the titles of locked (and `secret`) entries**: titles come from a static
  directory tree and Ageratum has no notion of a lock; hiding them would need a mixin into that
  tree. What `secret` buys is that the unlock condition is not named.
* A title inside a gate is no longer a top-level component of the document, so **anchors do not
  work inside a locked document** (no anchor links are emitted to begin with).
* **Nested categories are flattened**: Ageratum's sidebar only shows "documents of a top-level
  directory" and "a subdirectory's index", never a subdirectory's own documents, so the hierarchy
  folds into the file name (`patterns/great_spells/altiora` → `patterns/great_spells__altiora`) and
  a subdirectory's index becomes an ordinary document in its parent, sorted first by its front
  matter `weight`.
* **No `items:` front matter**: an entry's icon is not bound to an item, and the guide never grows
  behaviour the source book does not have.
* `hexcasting:crafting_multi`'s combined-input display is replaced by one recipe per variant (the
  difference is recorded in the report).
* When the book list changes, the component already on an existing item stack does not follow
  (deliberately).
* Exporting **writes several hundred files synchronously** (so that the chat output is in a
  predictable order), which stutters on a big book.
* Ageratum's scissor arithmetic is wrong while `scale != 1`, so this mod never draws book chrome, a
  facing page or widgets and therefore never touches a scissor.

## 5. Building and developing

```powershell
cd E:\miemod\PatchouliToAgratum
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'   # Gradle must run on JDK 21
.\gradlew.bat build                 # also runs the core's offline regression, the only automated check
.\gradlew.bat :pta-core:test        # the plain-JVM regression alone, no Minecraft needed
.\gradlew.bat runClient             # start a dev client
```

If a dependency is missing: `.\gradlew.bat --refresh-dependencies`.

**Two modules, one discipline**:

* `pta-core/` — the plain-JVM conversion core: the book model, JSON5 and `$(...)`/glyph parsing, page
  type strategy, Ageratum Markdown output. **It may not reference any Minecraft, Patchouli or
  Ageratum type** (they are not on its compile classpath), which is what lets it be unit-tested
  offline and reused wholesale elsewhere.
* the root project — the mod-side glue: entrypoint, config, virtual resource pack, the Ageratum
  extension components, the item, the commands, model overrides. The core's sources are **compiled
  into the main source set** (`sourceSets.main.java.srcDir(project(':pta-core')…)`) rather than added
  as an `implementation project(':pta-core')`: MDG's dev runtime classpath only resolves declared
  source sets plus a curated library list, so an ordinary project dependency compiles and then fails
  at runtime with `ClassNotFoundException`. The core's purity is guaranteed by the build itself —
  the same sources compile and run their tests with no Minecraft on the classpath, and `build`
  depends on that.

```
pta-core/src/main/java/cn/xm1221/pta/core/
├─ book/            BookLayout (categories/entries/locks) · BookConverter (documents) · BookSource · text conversion
│  └─ page/         PageTypeRegistry + one Renderer per page type (the fallback is registered last)
├─ lang/Json5       language file parsing and flattening
├─ text/            Patchouli text scanning, macro expansion, Ageratum Markdown writing
└─ report/          ConversionReport (coverage and lossy conversions)

src/main/java/cn/xm1221/pta/
├─ PtaMod · PtaConfig · PtaBookList · PtaGuides
├─ PtaComponents (Ageratum extension component registration) · PtaPageRenderers (page type wiring)
├─ PtaDataComponents (pta:guide) · PtaItems · item/GuideBookItem
└─ client/
   ├─ PtaClient (resource pack + commands + model events)
   ├─ component/   MDPatchouliPageComponent · MDHexPatternComponent · MDLockedComponent
   ├─ lock/PtaLocks · render/ (Patchouli page host + pattern reflection bridge) · model/ (per-component item models)
   ├─ source/ (ModFileBookSource · PtaGuideDocuments · PtaGuidePack · PtaPackFinder)
   └─ export/ · command/
```

A few traps worth knowing before writing code here (each is explained where it bites in the code):
an Ageratum `<row>` narrows its children to their **preferred width** (so a pattern or an image that
centres itself on `maxX()` ends up left-aligned), a component's **position comes from the pose, not
from offsets**, 1.21.1 and 1.21.4 have opposite scissor semantics, and a nested node in a language
file carries **its own separator** (`"advancement.hexcasting:"`, `"lore/"`).

## 6. Porting / deriving

For another Minecraft version, another loader, another target guide UI, or simply your own book —
what each road costs, which files it touches and which traps have to be stepped in again are in
**[docs/PORTING.md](docs/PORTING.md)** (in Chinese).

## 7. License

MIT. `TEMPLATE_LICENSE.txt` is the original licence notice of the NeoForge MDK template; the
Mojang mappings are additionally covered by their own licence,
<https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>.
