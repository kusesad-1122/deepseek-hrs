# DeepSeek HRS

DeepSeek Harness（DSH）的独立 Android APP：把官方 dsh web 完整装入手机，UI 零修改。

## 架构

```
Android APP (Kotlin + WebView)
  ├─ proot (termux 静态二进制)
  ├─ Ubuntu 24.04 arm64 rootfs（内置 Node 24 + dsh 全部依赖）
  └─ 启动流程：解压 rootfs → proot 启动 Ubuntu → node dsh --profile web → WebView 打开 127.0.0.1:3080
```

- 图标：DeepSeek 官方 favicon（鲸鱼）
- dsh 源码：未做任何修改；通过 `cordis.patch.yml` 禁用 node-pty 依赖链（Android 无预编译二进制）
- 构建：GitHub Actions（qemu-user-static 构建 arm64 rootfs + Gradle 打包 APK）

## 构建

```bash
git clone https://github.com/kusesad-1122/deepseek-hrs
# push 到 main 分支触发 Actions，或手动 workflow_dispatch
# 产物：Actions Artifacts → DeepSeek-HRS-apk
```

## 注意

- APK 体积约 200-300MB（内置完整 Linux 环境）
- 首次启动需解压 rootfs，约 1-3 分钟
- 需要约 1.5GB 内部存储空间
- dsh 为开发者预览版，官方声明核心 API 将持续迭代

## 版权

- DeepSeek Harness: MIT © DeepSeek
- 图标: DeepSeek 官方 favicon
- 本项目: MIT
