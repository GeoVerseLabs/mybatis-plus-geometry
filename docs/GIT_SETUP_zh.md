# Git 设置指南

本文档提供 mybatis-plus-geometry Git 仓库的设置说明。

[English](GIT_SETUP.md) | 简体中文

## 快速开始

### 自动设置

运行适合您平台的初始化脚本：

**Linux/Mac：**
```bash
chmod +x init-git.sh
./init-git.sh
```

**Windows：**
```cmd
init-git.bat
```

### 手动设置

如果您更喜欢手动设置：

```bash
# 1. 初始化 Git 仓库
git init

# 2. 配置提交信息模板
git config commit.template .gitmessage

# 3. 配置行尾
git config core.autocrlf input
git config core.eol lf

# 4. 设置默认分支
git config init.defaultBranch main

# 5. 添加远程仓库
git remote add origin https://github.com/yoy0o/mybatis-plus-geometry.git

# 6. 暂存所有文件
git add .

# 7. 进行初始提交
git commit -m "feat: initial commit"

# 8. 推送到 GitHub
git push -u origin main
```

## Git 配置文件

### .gitignore

从版本控制中排除构建产物、IDE 文件和敏感数据。

主要排除项：
- 构建目录（`build/`、`.gradle/`）
- IDE 文件（`.idea/`、`*.iml`、`.vscode/`）
- 敏感文件（`*.key`、`*.pem`、`secring.gpg`）
- 操作系统文件（`.DS_Store`、`Thumbs.db`）

### .gitattributes

确保跨平台的一致文件处理：
- 文本文件使用 LF 行尾
- Shell 脚本（`.sh`）始终使用 LF
- 批处理文件（`.bat`）始终使用 CRLF
- 二进制文件正确标记

### .editorconfig

在不同编辑器中保持一致的编码风格：
- Java：4 个空格，每行最多 120 个字符
- YAML：2 个空格
- Gradle：4 个空格

### .gitmessage

遵循约定式提交的提交信息模板：

```
<type>: <subject>

<body>

<footer>
```

类型：
- `feat`：新功能
- `fix`：Bug 修复
- `docs`：文档更改
- `style`：代码风格更改
- `refactor`：代码重构
- `test`：测试更改
- `chore`：构建/工具更改
- `perf`：性能改进
- `ci`：CI/CD 更改

## GitHub 配置

### Issue 模板

位于 `.github/ISSUE_TEMPLATE/`：
- `bug_report.md`：用于报告 Bug
- `feature_request.md`：用于请求功能

### Pull Request 模板

位于 `.github/pull_request_template.md`

包含检查清单：
- 代码风格合规性
- 测试
- 文档
- 自我审查

### Dependabot

在 `.github/dependabot.yml` 中配置：
- 每周检查 Gradle 依赖
- 每周检查 GitHub Actions
- 自动创建更新 PR

### 代码所有者

在 `.github/CODEOWNERS` 中定义：
- 自动请求维护者审查
- 确保对关键更改的适当监督

## 提交信息指南

### 格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 示例

```
feat(handler): 添加 MultiPolygon 类型支持

实现 JTS MultiPolygon 几何类型的 TypeHandler。
包括 WKB 转换和验证。

Closes #42
```

```
fix(interceptor): 修正别名列的 HEX 包装

SQL 拦截器未正确处理 SELECT 查询中的列别名。
此修复确保正确应用 HEX()。

Fixes #38
```

```
docs(readme): 更新安装说明

添加 Maven 和 Gradle 示例及最新版本。
包含常见问题的故障排除部分。
```

## 分支策略

### 主要分支

- `main`：生产就绪代码
- `develop`：功能集成分支

### 功能分支

格式：`feature/<issue-编号>-<简短描述>`

示例：`feature/42-multipolygon-support`

### Bug 修复分支

格式：`fix/<issue-编号>-<简短描述>`

示例：`fix/38-hex-wrapping-aliases`

### 发布分支

格式：`release/v<版本>`

示例：`release/v1.1.0`

## 工作流程

1. **创建功能分支**
   ```bash
   git checkout -b feature/42-multipolygon-support
   ```

2. **进行更改并提交**
   ```bash
   git add .
   git commit
   # 遵循提交信息模板
   ```

3. **推送到 GitHub**
   ```bash
   git push -u origin feature/42-multipolygon-support
   ```

4. **创建 Pull Request**
   - 前往 GitHub
   - 点击 "New Pull Request"
   - 填写 PR 模板
   - 请求审查

5. **合并后**
   ```bash
   git checkout main
   git pull origin main
   git branch -d feature/42-multipolygon-support
   ```

## 发布流程

1. **更新版本**
   ```bash
   # 更新 gradle.properties 中的版本
   vim gradle.properties
   ```

2. **更新变更日志**
   ```bash
   # 添加发布说明到 CHANGELOG.md
   vim CHANGELOG.md
   ```

3. **提交更改**
   ```bash
   git add gradle.properties CHANGELOG.md
   git commit -m "chore: 升级版本到 1.1.0"
   ```

4. **创建标签**
   ```bash
   git tag -a v1.1.0 -m "发布版本 1.1.0"
   ```

5. **推送标签**
   ```bash
   git push origin v1.1.0
   ```

6. **GitHub Actions 将自动：**
   - 构建项目
   - 运行测试
   - 发布到 Maven Central
   - 创建 GitHub Release

## 故障排除

### 行尾问题

如果您看到行尾警告：

```bash
git config core.autocrlf input
git rm --cached -r .
git reset --hard
```

### 大文件

如果您意外提交了大文件：

```bash
# 从历史记录中删除
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch path/to/large/file" \
  --prune-empty --tag-name-filter cat -- --all
```

### 撤销最后一次提交

```bash
# 软重置（保留更改）
git reset --soft HEAD~1

# 硬重置（丢弃更改）
git reset --hard HEAD~1
```

## 其他资源

- [Git 文档](https://git-scm.com/doc)
- [GitHub 指南](https://guides.github.com/)
- [约定式提交](https://www.conventionalcommits.org/zh-hans/)
- [语义化版本](https://semver.org/lang/zh-CN/)
