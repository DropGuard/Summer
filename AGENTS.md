<!-- gitnexus:start -->
# GitNexus

项目已索引为 **Summer**。索引过期时运行 `npx gitnexus analyze`。

探索用 `gitnexus_query`，大改动前用 `gitnexus_impact` 查影响范围，重命名用 `gitnexus_rename`。

## Build

- AOT 插件在 `execute()` 开头清空 `target/generated-sources/aot`，强制每次从源码重编。
- 新增 AOT 生成器时，确认输出目录在该清理路径下。

<!-- gitnexus:end -->
