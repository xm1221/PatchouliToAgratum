# Patchouli to Ageratum (`pta`)

English | [简体中文](README_zh_CN.md)

**Every Patchouli book installed becomes a real Ageratum guide.** The guides are generated in memory
while the game starts: nothing is written to disk, no resource reload is needed, and a book that a
pack adds later takes no work at all.

Its first target is Hex Casting's own book, but the converter itself is generic: any Patchouli book
can be mirrored. The result is a standalone add-on mod, permanently decoupled from Hex Casting — it
does not patch Hex Casting, does not build against it, and does not reference it on the classpath
(with the mod installed its book is there; without it, it is not).

* Minecraft **1.21.1** / NeoForge **21.1.236**
* Required at runtime: **Patchouli** `1.21.1-93-NEOFORGE` and **Ageratum** `0.0.1+build.121` (both client-side)
* mod id `pta` · package `cn.xm1221.pta` · version `0.1.0` · MIT

---

## 1. What you get

* **Every Patchouli book, mirrored.** No whitelist and no per-book setup: whatever books the pack has
  are converted, and `exclude.mods` is the only switch — a blacklist.
* **Prose that is still prose.** `patchouli:text` and `link` pages become native Markdown, so the
  text is selectable and searchable and its links navigate. `$(...)` macros, glyphs and `$(l:…)` links
  keep working, and the book's language files are read as they really are (JSON5 comments, unquoted
  keys, trailing commas, nested keys that carry their own separator).
* **Recipes as Ageratum's own components.** Crafting, smelting, blasting, smoking, campfire,
  smithing and stonecutting pages become `<recipe id="…"/>`, drawn by Ageratum from the real recipe,
  with ingredient tooltips to hover.
* **Items, blocks and entities are live too.** A `spotlight` becomes an `<item>`, a decorated entity
  an `<entity>`, and an image page a real Markdown image.
* **Hex Casting's pattern pages keep their hexagon.** Title, Input/Output and prose are native
  Markdown, and `<pta:pattern>` draws the pattern itself. A page that carries its own patterns — the
  ops with no shape of their own, like the number pattern or a mask — is drawn from those, so nothing
  is lost.
* **What cannot be converted is hosted, not approximated.** A page this mod cannot convert faithfully
  is drawn by **the real Patchouli page**, off-screen, inside the Ageratum document (`<pta:page>`).
  That covers all 16 built-in page types, other mods' template pages, their `IComponentProcessor`s and
  Hex Casting's own page types: zero reproduction cost, and nothing to drift when versions change.
  (Patchouli's `BookPage.render(...)` does not need a `Screen`, so a real `GuiBookEntry` can be built
  off-screen and painted in place.)
* **Advancement locks work.** An entry gated by an advancement, or a chapter whose every entry is
  locked, becomes a gate checked **per player, while drawing**: earn the advancement and the page
  opens on the spot, with nothing to reload.
* **A guide item for each book.** `pta:guidebook` carries a `pta:guide` component; its name and
  texture come from that book, and the creative tab offers one per mirrored book.
* **Craft a book into its guide, and back.** Both directions are shapeless recipes, each with the
  advancement that puts it in the recipe book (see §3).
* **See what was lost.** Every conversion produces a report: how many pages were converted, which
  were hosted, which language keys were missing, and where something had to be dropped.

How that runs, in one picture:

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

Two design lines run through all of it: **do not reimplement page types — host them**, which is why
the fallback loses nothing; and **what can be converted faithfully becomes native Markdown**, which
is why the result is selectable, searchable and clickable instead of a set of pictures.

## 2. What each page becomes

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
| Hex Casting pattern pages (`hexcasting:pattern` / `manual_pattern` / `manual_pattern_nosig`) | title, Input/Output and prose as native Markdown; the hexagon itself is drawn by `<pta:pattern>` (title on top, hexagon centred, Input/Output right under it, prose last) — a page that carries its own patterns draws those, which is how the ops with no shape of their own (the number pattern, a vector constant, a mask) keep their picture |
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

## 3. Installing and using it

Install it on the client (put `pta`, `patchouli` and `ageratum` in `mods/`) and start the game.

**Configuration**: `config/pta-common.toml`

| Key | Default | Meaning |
|---|---|---|
| `exclude.mods` | `[]` | mod **namespaces** whose books are left alone. For example `["hexcasting"]` |

Every Patchouli book found is mirrored — that is what "mirror a book" means — so exclusion is the
only switch. The config is **common** rather than client-side on purpose: the same list decides
which guides exist and which guide items the creative tab offers, and items are registered for both
sides. (A per-world server config could not do this — it does not exist yet while items are being
registered.)

> An excluded mod's book is logged and skipped, so "why is that book missing" is answerable.
> Configuration changes take effect on the **next start**: guides are generated while the game
> assembles its resource packs.
> Books are found in mods' own files, at `data/<namespace>/patchouli_books/<book>/`, because the
> guides are generated while the game assembles its resource packs — before Patchouli's own book
> list exists. A book that lives only in a datapack is therefore not found, and never was: the
> guide is generated from the book's files, not from Patchouli's loaded copy.
> `pta:spike` is a small practice book that ships with this mod, for exercising the mirroring
> pipeline without installing anything else; it is mirrored like any other book, so exclude `pta`
> to hide it.

**Item**: `pta:guidebook` (one per mirrored book, in the "Tools & Utilities" creative tab).

* With a `pta:guide` component (pointing at `namespace:book`) — its name is that book's name, its
  texture is that book's guide texture, and using it opens that guide.
* **Without** the component it is an Ageratum guidebook itself: Ageratum's name and texture, and
  using it does nothing.

**Crafting**: each mirrored book also gets a pair of recipes, written while the game loads because
the books are only known then.

* The Patchouli book becomes the `pta:guidebook` for that book, and that guide becomes the
  Patchouli book back.
* Both are shapeless, so the one item can go anywhere in the grid.
* Both name the book through a component. Patchouli hands every book out as
  `patchouli:guide_book` carrying `patchouli:book`, and the guides work the same way, so an
  ingredient that named only the item would match every book in the pack.
* Each recipe comes with the advancement that puts it in the recipe book once you hold the item
  being converted (Patchouli's book, or the guide).
* A book that Patchouli gives a `custom_book_item` is the one case this does not cover, because
  its book is not `patchouli:guide_book`: only the direction out of the guide applies to it.

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

## 5. Building

```powershell
cd E:\miemod\PatchouliToAgratum
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'   # Gradle must run on JDK 21
.\gradlew.bat build                 # also runs the core's offline regression, the only automated check
.\gradlew.bat :pta-core:test        # the plain-JVM regression alone, no Minecraft needed
.\gradlew.bat runClient             # start a dev client
```

If a dependency is missing: `.\gradlew.bat --refresh-dependencies`.

The two modules and the discipline between them (a plain-JVM conversion core compiled into the mod's
source set, plus the mod-side glue), the directory layout, and the traps this codebase bites back
with are in **[docs/DEVELOPING.md](docs/DEVELOPING.md)** (in Chinese).

## 6. Porting / deriving

For another Minecraft version, another loader, another target guide UI, or simply your own book —
what each road costs, which files it touches and which traps have to be stepped in again are in
**[docs/PORTING.md](docs/PORTING.md)** (in Chinese).

## 7. License

MIT. `TEMPLATE_LICENSE.txt` is the original licence notice of the NeoForge MDK template; the
Mojang mappings are additionally covered by their own licence,
<https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>.
