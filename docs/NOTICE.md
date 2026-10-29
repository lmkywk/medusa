# 第三方组件与许可清单（4.0.0）

分发包前必须**法务确认**。本清单按“能核实到的证据”填写，依据栏标明来源；无法核实的写“待核对”，不得当作已确认。

## 结论先行（需要法务看的）

1. **本项目从 Sesame 分支（GPL-3.0）改写而来**，仓库内 `Sesame-GR` / `Sesame-M-MIUIX-api102` 的 `LICENSE` 均为 GNU GPL v3。
   若对外分发 Medusa 的二进制，按 GPL-3.0 需要**同时提供完整对应源码**，并保留版权与许可声明。
2. **NPatch 也是 GPL-3.0**。它只作为构建工具使用，但产物（`origin-833-npatched.apk` 宿主）是分发物的一部分；
   工具输出是否构成衍生作品、是否触发 GPL 传染，**需法务判断**。
3. 余下依赖以 Apache-2.0 为主，兼容性风险低；`junit`(EPL-1.0) 与 `org.json`(Public Domain) 只在**测试**阶段使用，不进分发物。

## 运行时依赖（打进 APK）

| 组件 | 版本 | 用途 | 许可证 | 依据 |
|---|---|---|---|---|
| androidx.appcompat:appcompat | 1.8.0 | 兼容基础 | Apache-2.0 | 依赖 POM `<licenses>` |
| io.github.libxposed:api | 102.0.0 | Xposed 接口（compileOnly） | Apache-2.0 | 依赖 POM `<licenses>` |
| io.github.libxposed:service | 102.0.0 | 模块服务 | Apache-2.0 | 依赖 POM `<licenses>` |
| org.projectlombok:lombok | 1.18.48 | 编译期注解 | MIT | 依赖 POM `<licenses>` |
| com.elvishew:xlog | 1.11.1 | 日志 | Apache-2.0 | 依赖 POM `<licenses>` |
| com.squareup.okhttp3:okhttp | 4.12.0 | 网络（AI 客户端） | Apache-2.0 | 依赖 POM `<licenses>` |
| com.fasterxml.jackson.core:jackson-core | 2.22.3 | JSON | Apache-2.0 | 依赖 POM `<licenses>` |
| com.fasterxml.jackson.core:jackson-databind | 2.22.3 | JSON | Apache-2.0 | 依赖 POM `<licenses>` |
| com.fasterxml.jackson.core:jackson-annotations | 2.22 | JSON | Apache-2.0 | 依赖 POM `<licenses>` |
| org.nanohttpd:nanohttpd | 2.3.1 | 内嵌 HTTP（诊断/导出） | BSD-3-Clause | 上游 LICENSE.md（GitHub API 核实）；Maven POM 未声明 |
| org.jetbrains.kotlin:kotlin-stdlib | 2.4.20 | Kotlin 运行时 | Apache-2.0 | 依赖 POM `<licenses>` |
| androidx.activity:activity-compose | 1.13.0 | Compose Activity | Apache-2.0 | 依赖 POM `<licenses>` |
| top.yukonga.miuix.kmp:miuix-ui | 0.9.4 | UI 组件 | Apache-2.0 | 依赖 POM `<licenses>` |
| top.yukonga.miuix.kmp:miuix-preference | 0.9.4 | UI 组件 | Apache-2.0 | 依赖 POM `<licenses>` |
| androidx.compose.material:material-icons-extended | 1.7.8 | 图标 | Apache-2.0 | 依赖 POM `<licenses>` |

## 仅测试/构建（不进分发物）

| 组件 | 版本 | 用途 | 许可证 | 依据 |
|---|---|---|---|---|
| junit:junit | 4.13.2 | 单元测试 | EPL-1.0 | Maven Central POM |
| org.json:json | 20240303 | 测试期 JSON 实现 | Public Domain | Maven Central POM |
| Gradle Wrapper | 9.4.1 | 构建 | Apache-2.0 | 上游发行说明 |
| com.android.tools.build:gradle (AGP) | 9.2.1 | 构建 | Apache-2.0 | 上游发行说明 |
| org.jetbrains.kotlin gradle plugin | 2.4.20 | 构建 | Apache-2.0 | 上游发行说明 |
| Android SDK（platforms / build-tools） | 37 / 37.0.0 | 构建 | Android SDK 许可 | 不随包分发 |
| NPatch | 1.0.9 | 生成宿主 APK | **GPL-3.0** | 仓库 `npatch-src/NPatch-dev/LICENSE` |

## 参考实现（未直接复制，但设计源自）

| 项目 | 用途 | 许可证 | 依据 |
|---|---|---|---|
| Sesame-GR（dragon813） | 源码参考 + 部分继承 | **GPL-3.0** | `licenses/LICENSE-dragon813-sesame-gr.txt` |
| Sesame-M-MIUIX-api102 | 源码参考 + 部分继承 | **GPL-3.0** | 同上目录 `LICENSE` |
| XQuickEnergy（constanline / pansong291） | 早期任务实现参考 | Apache-2.0 | `licenses/LICENSE-xquickenergy-*.txt` |
| LSPosed | 模块加载框架（不打包） | GPL-3.0 | `licenses/LICENSE-lsposed.txt` |

## 待核对（分发前必须确认）

- [ ] 仓库内是否残留 Sesame 原版版权头/文件，若有需按 GPL-3.0 保留。
- [ ] 首页默认 hero 背景 `home_default.jpg` 由用户提供（AI 生成素材），来源与商用授权需确认。
- [ ] APK 内 `assets/`、图标、字体是否含第三方素材（当前未发现独立素材来源记录）。
- [ ] NPatch 产物分发的 GPL 义务边界（是否必须随包提供 NPatch 源码）。
- [ ] `Sesame-M-MIUIX-api102/licenses` 下 6 份许可文本是否仍需随包附带。
- [ ] 宿主 APK 内嵌入模块与支付宝原始 APK 的关系（`origin.apk` 的来源与授权）。

## 复核方式

```bash
# 依赖许可证可随时重新核对
bash tools/ci.sh licenses
```

`tools/ci.sh licenses` 会拉取各依赖 POM 的 `<licenses>` 段并打印，与本表比对。