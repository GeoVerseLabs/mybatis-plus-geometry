# 贡献指南

[English](CONTRIBUTING.md) | 简体中文

感谢您对本项目的关注！本文档提供贡献的指南和说明。

## 行为准则

请在所有互动中保持尊重和建设性。我们欢迎所有经验水平的贡献者。

## 如何贡献

### 报告 Bug

1. 在 [Issues](https://github.com/GeoVerseLabs/mybatis-plus-geometry/issues) 中检查 Bug 是否已被报告
2. 如果没有，创建新 Issue，包含：
   - 清晰的标题和描述
   - 重现步骤
   - 预期行为 vs 实际行为
   - 环境详情（Java 版本、Spring Boot 版本、数据库）

### 建议功能

1. 使用 `enhancement` 标签打开 Issue
2. 描述功能及其用例
3. 讨论实现方法

### Pull Request

1. Fork 仓库
2. 创建功能分支：`git checkout -b feature/your-feature-name`
3. 进行更改
4. 编写或更新测试
5. 确保所有测试通过：`./gradlew test`
6. 使用清晰的信息提交
7. 推送并创建 Pull Request

## 开发环境设置

### 前置要求

- JDK 17 或更高版本
- Gradle 8.x（包含 wrapper）
- 支持 Lombok 的 IDE

### 构建

```bash
# 克隆您的 fork
git clone https://github.com/YOUR_USERNAME/mybatis-plus-geometry.git
cd mybatis-plus-geometry

# 构建
./gradlew build

# 运行测试
./gradlew test
```

### 代码风格

- 遵循标准 Java 约定
- 使用有意义的变量和方法名
- 为公共 API 添加 Javadoc
- 保持方法专注和简短
- 为新功能编写测试

### 提交信息

使用清晰、描述性的提交信息：

```
feat: 添加 MultiPolygon 类型支持
fix: 修正空几何的 WKB 解析
docs: 更新 README 添加 PostgreSQL 示例
test: 添加坐标验证的属性测试
```

提交类型：
- `feat`：新功能
- `fix`：Bug 修复
- `docs`：文档更改
- `style`：代码风格更改
- `refactor`：代码重构
- `test`：测试更改
- `chore`：构建/工具更改
- `perf`：性能改进
- `ci`：CI/CD 更改

## 测试

- 为新功能编写单元测试
- 包含边界情况
- 提交 PR 前运行完整测试套件

## 代码审查

提交 PR 后：

1. 维护者将审查您的代码
2. 根据反馈进行必要的更改
3. 所有检查通过后，PR 将被合并

## 文档

- 更新相关文档（README、API 文档等）
- 为新功能添加使用示例
- 保持中英文文档同步

## 问题？

如有任何关于贡献的问题，请随时打开 Issue。

感谢您的贡献！🎉

