# 为 OneLedger 贡献

开始前请阅读[项目规范中心](docs/README.md)、[实施路线图与分支地图](docs/IMPLEMENTATION_ROADMAP.md)和[Git 协作规范](docs/GIT_WORKFLOW.md)。

最短流程：从最新 `main` 为一个路线图工作包创建语义化分支；每次编辑文件的对话完成验证后都创建一个原子 commit；完成[质量清单](docs/QUALITY_CHECKLIST.md)；推送并按 PR 模板提供证据；评审和检查通过后 Squash 合并。

本地验证：

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug validateDebugScreenshotTest compileDebugAndroidTestKotlin
```

涉及 Room schema、Migration 或 DAO 行为时，另在允许 USB 安装测试 APK 的设备上运行 `.\gradlew.bat connectedDebugAndroidTest`。

安全问题请不要提交包含真实账单、密钥或签名文件的公开 Issue/PR。
