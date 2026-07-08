/* tslint:disable */
/* eslint-disable */
/* prettier-ignore */
// @ts-nocheck
// noinspection JSUnusedGlobalSymbols
import { execSync } from "node:child_process";
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { join, relative, resolve } from "node:path";

// ══════════════════════════════════════════════════════════════════════════════
// 同步配置 - 请修改以下变量以适配你的项目
// ══════════════════════════════════════════════════════════════════════════════

// 当前仓库的 GitHub 路径，格式: "owner/repo"
// ⚠️ 请将此值修改为你自己的仓库路径
const CURRENT_REPO = "mymx2/mica-auto-ksp";

// 上游模板仓库的 GitHub 路径
const UPSTREAM_REPO = "mymx2/starter-gradle";

// ══════════════════════════════════════════════════════════════════════════════

const PROJECT_ROOT = resolve(import.meta.dirname, "..", "..", "..");
const WORKTREE_DIR = join(PROJECT_ROOT, ".worktrees", "starter-gradle");

// ── 跳过不同步的目录（上游有但本项目不需要） ─────────────────────────────────
// 这些目录是上游模板的内容，本项目不使用
const SKIP_DIRS = [
  ".github/actions",     // 上游是完整 action 代码，本地为空
  "app",                 // 上游示例模块
  "docs",                // 上游文档站点
  "examples",            // 上游示例代码
  "gradle/build-logic",  // 上游构建逻辑
];

// ─ 保留本地版本的文件（改动太大，不覆盖） ───────────────────────────────────
// 这些文件即使上游有更新也保留本地版本
const KEEP_LOCAL_FILES = [
  "README.md",
  "README_CN.md",
  "settings.gradle.kts",
  "settings-gradle.lockfile",
  "LICENSE",
  "gradle/depLibs.versions.toml",  // 本地独有
  "gradle/configs/detekt/detekt.yml", // 本地 detekt 配置，不覆盖
  "gradle.properties"
];

// ── 从上游覆盖后需要替换仓库引用的文件 ───────────────────────────────────────
const REPLACE_REPO_FILES = [
  ".github/workflows/git-sync-gitee.yml",
  ".github/workflows/publish-snapshot.yml",
  ".github/workflows/publish-release.yml",
  "gradle.properties",
];

// ── 系统目录，永远跳过 ───────────────────────────────────────────────────────
const SYSTEM_DIRS = [".git", ".worktrees", ".gradle", "build", ".kotlin", "node_modules"];

// ── 同步统计计数器 ────────────────────────────────────────────────────────────
const stats = { synced: 0, skipped: 0, keepLocal: 0 };

// ── 辅助函数 ────────────────────────────────────────────────────────────────

function run(cmd: string, cwd?: string): void {
  console.log(`> ${cmd}`);
  execSync(cmd, { cwd, stdio: "inherit" });
}

function normalize(path: string): string {
  return path.replace(/\\/g, "/");
}

/**
 * 判断相对路径是否应跳过：
 * - 顶层名称命中 SYSTEM_DIRS（如 .git, build）
 * - 路径命中 SKIP_DIRS（支持多段路径如 "gradle/build-logic"）
 */
function shouldSkip(relPath: string): boolean {
  const n = normalize(relPath);
  const topName = n.split("/")[0];
  return SYSTEM_DIRS.includes(topName) || isInSkipPath(n);
}

function isKeepLocal(relPath: string): boolean {
  const n = normalize(relPath);
  for (const f of KEEP_LOCAL_FILES) {
    if (n === f) return true;
  }
  return false;
}

/**
 * 判断相对路径是否在应跳过的目录下
 */
function isInSkipPath(relPath: string): boolean {
  const n = normalize(relPath);
  for (const d of SKIP_DIRS) {
    if (n === d || n.startsWith(d + "/")) return true;
  }
  return false;
}

/**
 * 递归同步目录：从上游（src）复制到本地（dest）
 * - 跳过系统目录和本项目不需要的目录
 * - 覆盖本地已有文件（保留本地版本的文件除外）
 */
function syncDir(src: string, dest: string): void {
  if (!existsSync(src)) {
    console.log(`  [warn] Source not found: ${src}`);
    return;
  }

  mkdirSync(dest, { recursive: true });
  const entries = readdirSync(src);

  for (const entry of entries) {
    const srcPath = join(src, entry);
    const destPath = join(dest, entry);
    const relPath = normalize(relative(WORKTREE_DIR, srcPath));

    // 跳过系统目录和配置中声明的跳过目录
    if (shouldSkip(relPath)) {
      stats.skipped++;
      continue;
    }

    const stat = statSync(srcPath);

    if (stat.isDirectory()) {
      syncDir(srcPath, destPath);
    } else {
      // 保留本地版本的文件不覆盖
      if (isKeepLocal(relPath)) {
        stats.keepLocal++;
        continue;
      }
      copyFileSync(srcPath, destPath);
      stats.synced++;
    }
  }
}

/**
 * 替换文件中的仓库引用：上游仓库 → 当前仓库
 */
function replaceRepoRefs(filePath: string): void {
  if (!existsSync(filePath)) return;

  let content = readFileSync(filePath, "utf-8");
  const original = content;

  // replaceAll 已覆盖所有包含 UPSTREAM_REPO 子串的内容（包括 URL 和 SSH 路径）
  content = content.replaceAll(UPSTREAM_REPO, CURRENT_REPO);

  if (content !== original) {
    writeFileSync(filePath, content, "utf-8");
    console.log(`  [replace-repo] ${normalize(relative(PROJECT_ROOT, filePath))}`);
  }
}

// ── 主流程 ──────────────────────────────────────────────────────────────────

// ── Step 1: Clone or update upstream repo ──────────────────────────────────

console.log("\n📦 Step 1: Clone / update upstream repo");

if (existsSync(join(WORKTREE_DIR, ".git"))) {
  console.log("  Upstream already cloned, pulling latest...");
  try {
    run("git pull --ff-only", WORKTREE_DIR);
  } catch {
    console.error("  [error] git pull --ff-only failed, upstream may have diverged.");
    console.error("  Please resolve manually: cd " + WORKTREE_DIR);
    process.exit(1);
  }
} else {
  console.log("  Cloning upstream...");
  mkdirSync(join(PROJECT_ROOT, ".worktrees"), { recursive: true });
  run(`git clone --branch main https://github.com/${UPSTREAM_REPO}.git ${WORKTREE_DIR}`);
}

// ── Step 2: Sync all files from upstream ──────────────────────────────────

console.log("\n🔄 Step 2: Sync project files from upstream");

syncDir(WORKTREE_DIR, PROJECT_ROOT);

// ── Step 3: Replace repo references in synced files ─────────────────────────

console.log("\n✏️  Step 3: Replace repo references");

for (const relPath of REPLACE_REPO_FILES) {
  const filePath = join(PROJECT_ROOT, relPath);
  replaceRepoRefs(filePath);
}

console.log("  [ok] All repo references replaced in synced files");
console.log(`  synced: ${stats.synced}, skipped: ${stats.skipped}, keep-local: ${stats.keepLocal}`);

console.log("\n✅ Sync complete!");
