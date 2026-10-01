---
title: "Spike: hosting Patchouli pages"
items: "minecraft:diamond"
---

# Spike: hosting Patchouli pages

This document exists only to verify the core assumption of this mod: that a **real**
Patchouli page can be constructed off-screen and drawn as an ordinary Ageratum block
component.

Open it with `/ageratum pta`, or hold `W` while pointing at a diamond.

Each section below hosts one page of the throwaway `pta:spike` Patchouli book. The page is
produced by Patchouli itself — this mod does not implement any page type.

## Page 0 — text

<pta:page book="pta:spike" entry="spike" page="0"/>

## Page 1 — spotlight

<pta:page book="pta:spike" entry="spike" page="1"/>

## Page 2 — crafting recipe

<pta:page book="pta:spike" entry="spike" page="2"/>

## Page 3 — image

<pta:page book="pta:spike" entry="spike" page="3"/>

## Page 4 — entity

<pta:page book="pta:spike" entry="spike" page="4"/>

## Page 5 — link

<pta:page book="pta:spike" entry="spike" page="5"/>

## Appearance comparison

By default only the page body is drawn, with nothing behind it, so the page reads as part of
the guide rather than as a window onto another book:

<pta:page book="pta:spike" entry="spike" page="1"/>

Passing `look="book"` draws the whole surrounding book page instead. That is the only mode
which needs a scissor, and it is kept purely to show the difference:

<pta:page book="pta:spike" entry="spike" page="1" look="book"/>

## What to look for

* Every page keeps its own layout: the spotlight banner, the crafting grid, the bordered
  image and the entity preview should all be visually identical to the same page in the real
  Patchouli book.
* The page must be **complete from its first line**. A missing top band is the failure this
  document exists to catch.
* `look="book"` is expected to look wrong: it demonstrates the clipping bug that the default
  appearance avoids entirely.
* Nothing is clickable yet. Input forwarding is deliberately not wired up in this pass.
