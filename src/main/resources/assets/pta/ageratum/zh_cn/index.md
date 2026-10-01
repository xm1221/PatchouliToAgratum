---
title: "Spike：内嵌 Patchouli 页面"
items: "minecraft:diamond"
---

# Spike：内嵌 Patchouli 页面

这份文档只用来验证本模组的核心假设：**真正的** Patchouli 页面能否在离屏状态下被构建，
并当作一个普通的藿香块级组件绘制出来。

用 `/ageratum pta` 打开，或者对着钻石长按 `W`。

下面每一节都内嵌了临时手册 `pta:spike` 的一页。页面由 Patchouli 自己渲染，本模组没有
实现任何一种页型。

## 第 0 页 —— 文本

<pta:page book="pta:spike" entry="spike" page="0"/>

## 第 1 页 —— 聚光物品

<pta:page book="pta:spike" entry="spike" page="1"/>

## 第 2 页 —— 合成配方

<pta:page book="pta:spike" entry="spike" page="2"/>

## 第 3 页 —— 图片

<pta:page book="pta:spike" entry="spike" page="3"/>

## 第 4 页 —— 实体

<pta:page book="pta:spike" entry="spike" page="4"/>

## 第 5 页 —— 链接

<pta:page book="pta:spike" entry="spike" page="5"/>

## 外观对比

默认只画页面本身，背后不加任何东西，于是页面读起来像是导读的一部分，而不是「另一本书的
一扇窗」：

<pta:page book="pta:spike" entry="spike" page="1"/>

传 `look="book"` 会改成连周围整张书面一起画。**只有这一种需要裁剪**，留着纯粹是为了对比：

<pta:page book="pta:spike" entry="spike" page="1" look="book"/>

## 观察要点

* 每一页都要保留自己的版式：聚光横幅、合成格、带边框的图片、实体预览，都应与它在真正的
  Patchouli 手册里长得一模一样。
* 页面必须**从第一行起就是完整的**。顶部缺一条，正是这份文档要抓的故障。
* `look="book"` 预期就是错的：它是用来展示那个被默认外观彻底绕开的裁剪缺陷的。
* 目前还没有任何可点击的东西，输入转发是刻意留到这一步之后的。
