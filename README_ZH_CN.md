# Zalith Launcher 2+ (PLUS)

[English](./README.md) | [**「中文（简体）」**](./README_ZH_CN.md) | [中文（台灣）](./README_ZH_TW.md) | [Türkçe](./README_TR.md)

> **⚠️ 非官方修改版本**
>
> 这是 [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2) 的非官方分支。本项目**与官方 Zalith Launcher 项目无关，也未获得其认可**。

**Zalith Launcher 2+** 是一款专为 [Minecraft: Java Edition](https://www.minecraft.net/) 定制的 **Android 设备**启动器，由社区修改而来。它基于 [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2) 的基础上，使用 [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher/tree/v3_openjdk/app_pojavlauncher/src/main/jni) 作为核心启动引擎，并采用 **Jetpack Compose** 和 **Material Design 3** 构建现代化用户界面。

## 统计数据

2026年7月14日：
除合并提交外，3位作者已向主分支推送了265次提交，所有分支共推送了266次提交。

在主分支上，共修改了288个文件，新增了10,634行代码，删除了3,095行代码。

## 📋 本分支的新特性

本分支旨在增强和定制原始 Zalith Launcher 2 的体验。主要改进包括：

- [x] 披风系统
- [x] 问题修复
- [x] 离线账户
- [x] 彩色名称
- [x] 主屏幕快捷方式
- [x] 导入/导出设置
- [x] 导入/导出账户（包含披风、皮肤和彩色名称）
- [x] 集成带配置的 mobileglues
- [x] 配置界面重制

还有更多无法一一列举的改进……

## 🔗 上游项目

本项目源自 Zalith Launcher 团队的优秀成果：
- **原始项目：** [ZalithLauncher2](https://github.com/ZalithLauncher/ZalithLauncher2)
- **原始许可证：** GPL-3.0

如需官方未经修改的版本，请访问上游项目。

## 🌐 语言和翻译支持

本分支使用 Zalith Launcher 2 的翻译。如需贡献翻译或改进，请考虑向上游的 [Zalith Launcher 2 Weblate 项目](https://hosted.weblate.org/projects/zalithlauncher2) 提交贡献。

## 📦 构建说明（面向开发者）

### 环境要求

* Android Studio **Bumblebee** 或更新版本
* Android SDK：
  * **最低 API 级别**：26
  * **目标 API 级别**：35
* JDK 11

### 构建步骤

```bash
git clone https://github.com/Star1xr/ZalithLauncher2Plus.git
# 在 Android Studio 中打开项目并进行构建
```

## 📜 许可证

本项目采用 **[GPL-3.0 许可证](LICENSE)**，继承自上游 Zalith Launcher 2 项目。

### 重要条款

**贡献和署名**
   - 所有修改均有清晰记录并归属于本分支
   - 上游项目已得到适当致谢

## 📦 开源库和许可证

本项目继承了 Zalith Launcher 2 的所有依赖项。有关完整的开源库及其许可证列表，请参阅原始项目的 [README](https://github.com/ZalithLauncher/ZalithLauncher2/blob/main/README.md)。

## 🤝 贡献

这是一个社区分支。在贡献之前，请：

1. 查阅 [CONTRIBUTING.md](./CONTRIBUTING.md) 指南
2. 检查现有议题和拉取请求
3. 遵循代码风格和规范
4. 清晰记录你的更改

## ⚠️ 支持和免责声明

- 这是一个**非官方分支**。官方支持请向[上游 Zalith Launcher 2 项目](https://github.com/ZalithLauncher/ZalithLauncher2)寻求
- 请在本仓库的议题追踪器中报告错误
- 对于上游相关的问题，请考虑先向原始项目报告
- 使用风险自负。本分支不提供任何官方保证或支持

## 🔒 安全与隐私

- 请始终从本官方仓库下载
- 警惕声称分发本软件的第三方网站
- 保护你的个人信息和凭证
- 通过议题追踪器负责任地报告安全问题

## 📞 联系与链接

- **原始项目：** https://github.com/ZalithLauncher/ZalithLauncher2
- **本分支：** https://github.com/Star1xr/ZalithLauncher2Plus

---

**Zalith Launcher 2** 是由 Zalith Launcher 团队创建和维护的原始项目。  
**Zalith Launcher 2+** 是为提供增强功能和修改而创建的非官方社区分支。

| Library                               | Copyright                                                                                                     | License              | Official Link                                                                     |
|---------------------------------------|---------------------------------------------------------------------------------------------------------------|----------------------|-----------------------------------------------------------------------------------|
| androidx-appcompat                    | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [链接↗](https://developer.android.com/jetpack/androidx/releases/appcompat)         |
| androidx-constraintlayout-compose     | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [链接↗](https://developer.android.com/develop/ui/compose/layouts/constraintlayout) |
| androidx-webkit                       | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [链接↗](https://developer.android.com/jetpack/androidx/releases/webkit)            |
| ANGLE                                 | Copyright 2018 The ANGLE Project Authors                                                                      | BSD 3-Clause License | [链接↗](http://angleproject.org/)                                                  |
| Apache Commons Codec                  | -                                                                                                             | Apache 2.0           | [链接↗](https://commons.apache.org/proper/commons-codec)                           |
| Apache Commons Compress               | -                                                                                                             | Apache 2.0           | [链接↗](https://commons.apache.org/proper/commons-compress)                        |
| Apache Commons IO                     | -                                                                                                             | Apache 2.0           | [链接↗](https://commons.apache.org/proper/commons-io)                              |
| ByteHook                              | Copyright © 2020-2024 ByteDance, Inc.                                                                         | MIT License          | [链接↗](https://github.com/bytedance/bhook)                                        |
| BuildKeys                             | Copyright © 2026 MovTery                                                                                      | Aoache 2.0           | [链接↗](https://github.com/MovTery/BuildKeys)                                      |
| Coil Compose                          | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [链接↗](https://github.com/coil-kt/coil)                                           |
| Coil Gifs                             | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [链接↗](https://github.com/coil-kt/coil)                                           |
| Coil SVG                              | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [链接↗](https://github.com/coil-kt/coil)                                           |
| Fishnet                               | Copyright © 2025 Kyant                                                                                        | Apache 2.0           | [链接↗](https://github.com/Kyant0/Fishnet)                                         |
| gl4es_extra_extra                     | Copyright © 2016-2018 Sebastien Chevalier; Copyright (c) 2013-2016 Ryan Hileman                               | MIT License          | [链接↗](https://github.com/PojavLauncherTeam/gl4es_extra_extra)                    |
| Gson                                  | Copyright © 2008 Google Inc.                                                                                  | Apache 2.0           | [链接↗](https://github.com/google/gson)                                            |
| kotlinx.coroutines                    | Copyright © 2000-2020 JetBrains s.r.o.                                                                        | Apache 2.0           | [链接↗](https://github.com/Kotlin/kotlinx.coroutines)                              |
| ktor-client-content-negotiation       | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [链接↗](https://ktor.io)                                                           |
| ktor-client-core                      | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [链接↗](https://ktor.io)                                                           |
| ktor-client-okhttp                    | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [链接↗](https://ktor.io)                                                           |
| ktor-http                             | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [链接↗](https://ktor.io)                                                           |
| ktor-serialization-kotlinx-json       | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [链接↗](https://ktor.io)                                                           |
| LWJGL - Lightweight Java Game Library | Copyright © 2012-present Lightweight Java Game Library All rights reserved.                                   | BSD 3-Clause License | [链接↗](https://github.com/LWJGL/lwjgl3)                                           |
| material-color-utilities              | Copyright 2021 Google LLC                                                                                     | Apache 2.0           | [链接↗](https://github.com/material-foundation/material-color-utilities)           |
| Maven Artifact                        | Copyright © The Apache Software Foundation                                                                    | Apache 2.0           | [链接↗](https://github.com/apache/maven/tree/maven-3.9.9/maven-artifact)           |
| Media3                                | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [链接↗](https://developer.android.com/jetpack/androidx/releases/media3)            |
| Mesa                                  | Copyright © The Mesa Authors                                                                                  | MIT License          | [链接↗](https://mesa3d.org/)                                                       |
| MMKV                                  | Copyright © 2018 THL A29 Limited, a Tencent company.                                                          | BSD 3-Clause License | [链接↗](https://github.com/Tencent/MMKV)                                           |
| Navigation 3                          | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [链接↗](https://developer.android.com/jetpack/androidx/releases/navigation3)       |
| NG-GL4ES                              | Copyright © 2016-2018 Sebastien Chevalier; Copyright © 2013-2016 Ryan Hileman; Copyright (c) 2025-2026 BZLZHH | MIT License          | [链接↗](https://github.com/BZLZHH/NG-GL4ES)                                        |
| OkHttp                                | Copyright © 2019 Square, Inc.                                                                                 | Apache 2.0           | [链接↗](https://github.com/square/okhttp)                                          |
| Okio                                  | Copyright © 2013 Square, Inc.                                                                                 | Apache 2.0           | [链接↗](https://square.github.io/okio/)                                            |
| OpenNBT                               | Copyright © 2013-2021 Steveice10.                                                                             | MIT License          | [链接↗](https://github.com/GeyserMC/OpenNBT)                                       |
| Process Phoenix                       | Copyright © 2015 Jake Wharton                                                                                 | Apache 2.0           | [链接↗](https://github.com/JakeWharton/ProcessPhoenix)                             |
| proxy-client-android                  | -                                                                                                             | LGPL-3.0 License     | [链接↗](https://github.com/TouchController/TouchController)                        |
| Reorderable                           | Copyright © 2023 Calvin Liang                                                                                 | Apache 2.0           | [链接↗](https://github.com/Calvin-LL/Reorderable)                                  |
| skinview3d                            | Copyright © 2014-2018 Kent Rasmussen; Copyright © 2017-2022 Haowei Wen, Sean Boult and contributors           | MIT License          | [链接↗](https://github.com/bs-community/skinview3d)                                |
| sora-editor                           | Copyright © 1991, 1999 Free Software Foundation, Inc.                                                         | LGPL-2.1 License     | [链接↗](https://github.com/Rosemoe/sora-editor)                                    |
| StringFog                             | Copyright © 2016-2023, Megatron King                                                                          | Apache 2.0           | [链接↗](https://github.com/MegatronKing/StringFog)                                 |
| XZ for Java                           | Copyright © The XZ for Java authors and contributors                                                          | 0BSD License         | [链接↗](https://tukaani.org/xz/java.html)                                          |
