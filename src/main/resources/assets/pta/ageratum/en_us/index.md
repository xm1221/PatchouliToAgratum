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

## Chrome comparison

The blocks above draw the page body only. The one below additionally asks for the
surrounding book page and paper texture with `chrome="true"`, which is the mode that has to
be clipped:

<pta:page book="pta:spike" entry="spike" page="1" chrome="true"/>

## What to look for

* Every page keeps its own layout: the spotlight banner, the crafting grid, the bordered
  image and the entity preview should all be visually identical to the same page in the real
  Patchouli book.
* The page body should be **complete from its first line**. A missing top band means the
  drawing area and the clip rectangle disagree.
* In page-only mode nothing needs clipping, so any missing content there is a layout problem
  rather than a clipping one. In chrome mode, only the page should show — no book margins, no
  second page, no navigation arrows.
* Nothing should be clickable yet. Input forwarding is deliberately not wired up in this
  first pass.
