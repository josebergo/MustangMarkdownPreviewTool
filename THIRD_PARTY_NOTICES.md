# 第三方组件

- Phosphor Icons：MIT，`third_party/Phosphor-LICENSE.txt`。书架、加入、目录、返回、文档图标由 [官方 SVG](https://github.com/phosphor-icons/core) 转为 Vector Drawable，图形路径保持原样。
- CommonMark Java 0.24.0 及其 GFM 表格、删除线、任务列表、自动链接扩展：BSD 2-Clause，`third_party/CommonMark-LICENSE.txt`。[官方项目](https://github.com/commonmark/commonmark-java)。
- highlight.js 11.11.1：BSD 3-Clause，`third_party/HighlightJS-LICENSE.txt`。[官方分发](https://github.com/highlightjs/cdn-release/tree/11.11.1/build)，打包的 `highlight.min.js` SHA-256：`c4a399dd6f488bc97a3546e3476747b3e714c99c57b9473154c6fb8d259b9381`。脚本在本地运行，不依赖 CDN。
- AndroidX / Compose：Apache 2.0；Kotlin：Apache 2.0。具体版本固定在 `gradle/libs.versions.toml`，组件许可证随 Maven 包提供。
- jsoup（仅测试）：MIT；JUnit 4（仅测试）：EPL 1.0。

验收样本中的 W3C 标志仅作为真实 HTTPS 加载测试资源，不打包为应用品牌或图标。
