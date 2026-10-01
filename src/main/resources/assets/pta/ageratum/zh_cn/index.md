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

## 带书页外观的对比

上面各块只画页面本身。下面这一块额外用 `chrome="true"` 要求连同周围的书面与纸张纹理一起
画出来——**这一种才是需要裁剪的**：

<pta:page book="pta:spike" entry="spike" page="1" chrome="true"/>

## 观察要点

* 每一页都要保留自己的版式：聚光横幅、合成格、带边框的图片、实体预览，都应与它在真正的
  Patchouli 手册里长得一模一样。
* 页面应当**从第一行起就是完整的**。顶部缺一条，说明绘制区域与裁剪矩形对不上。
* 纯页面模式下不需要任何裁剪，所以那种模式下若还缺内容，就是布局问题而不是裁剪问题。
  带书页外观的模式下只应看到那一页：不该有书页边缘、另一半页或翻页箭头。
* 目前还没有任何可点击的东西，输入转发是刻意留到下一步的。
