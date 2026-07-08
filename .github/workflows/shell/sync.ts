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
  rmSync,
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
const stats = { synced: 0, skipped: 0, keepLocal: 0, deleted: 0 };

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
 * 判断两个文件集合是否完全相同（即上游没有变化）
 */
function setsEqual(a: Set<string>, b: Set<string>): boolean {
  if (a.size !== b.size) return false;
  for (const item of a) {
    if (!b.has(item)) return false;
  }
  return true;
}

/**
 * 获取上游 worktree 的所有已追踪文件路径（相对于 worktree 根目录）
 */
function getUpstreamFiles(): Set<string> {
  const output = execSync("git ls-tree -r --name-only HEAD", {
    cwd: WORKTREE_DIR,
    encoding: "utf-8",
  });
  return new Set(output.trim().split("\n").map((s) => s.trim()).filter(Boolean));
}

/**
 * 递归同步目录：从上游（src）复制到本地（dest）
 * - 跳过系统目录和本项目不需要的目录
 * - 覆盖本地已有文件（保留本地版本的文件除外）
 * - 不执行任何删除操作（删除由 cleanupDeletedFiles 统一处理）
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
 * 根据上游 git 历史精确清理本地文件：
 * 只删除「上游曾有（beforePull）但上游已移除（afterPull）」的文件。
 * 本地独有的文件/目录从未出现在上游 git 历史中，因此永远不会被删除。
 */
function cleanupDeletedFiles(beforePull: Set<string>, afterPull: Set<string>): void {
  for (const filePath of beforePull) {
    // 上游仍然有的文件不删
    if (afterPull.has(filePath)) continue;

    // 跳过路径不处理
    if (isInSkipPath(filePath)) continue;

    // 保留本地文件不删
    if (isKeepLocal(filePath)) continue;

    const localPath = join(PROJECT_ROOT, filePath);
    if (existsSync(localPath)) {
      rmSync(localPath, { force: true });
      stats.deleted++;
      console.log(`  [deleted] ${filePath}`);
    }
  }

  // 清理删除文件后可能产生的空目录（自底向上）
  cleanEmptyDirs(PROJECT_ROOT);
}

/**
 * 递归清理空目录（不删除系统目录和含文件的目录）
 */
function cleanEmptyDirs(dir: string): void {
  if (!existsSync(dir)) return;
  const entries = readdirSync(dir);
  for (const entry of entries) {
    if (SYSTEM_DIRS.includes(entry)) continue;
    const fullPath = join(dir, entry);
    if (!existsSync(fullPath) || !statSync(fullPath).isDirectory()) continue;
    // 先递归子目录
    cleanEmptyDirs(fullPath);
    // 子目录清理后再检查当前目录是否为空
    if (readdirSync(fullPath).length === 0) {
      const relPath = normalize(relative(PROJECT_ROOT, fullPath));
      if (!isInSkipPath(relPath)) {
        rmSync(fullPath, { recursive: true, force: true });
        console.log(`  [empty-dir] ${relPath}`);
      }
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

// 在 pull 前记录上游文件快照（用于后续精确计算删除集）
let upstreamBefore: Set<string> = new Set();

if (existsSync(join(WORKTREE_DIR, ".git"))) {
  console.log("  Upstream already cloned, pulling latest...");
  upstreamBefore = getUpstreamFiles();
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

const upstreamAfter = getUpstreamFiles();

// ── 快速退出：上游无变化时跳过同步 ─────────────────────────────────────────

if (upstreamBefore.size > 0 && setsEqual(upstreamBefore, upstreamAfter)) {
  console.log("\n⏩ No upstream changes detected, skipping sync.");
  console.log("✅ Already up to date!");
  process.exit(0);
}

// ── 快速退出：上游无变化时跳过同步 ─────────────────────────────────────────

if (upstreamBefore.size > 0 && setsEqual(upstreamBefore, upstreamAfter)) {
  console.log("\n⏩ No upstream changes detected, skipping sync.");
  console.log("✅ Already up to date!");
  process.exit(0);
}

// ── Step 2: Sync all files from upstream ──────────────────────────────────

console.log("\n🔄 Step 2: Sync project files from upstream");

syncDir(WORKTREE_DIR, PROJECT_ROOT);

// ── Step 3: Cleanup files removed by upstream ──────────────────────────────

if (upstreamBefore.size > 0) {
  console.log("\n🧹 Step 3: Cleanup upstream-deleted files");
  cleanupDeletedFiles(upstreamBefore, upstreamAfter);
} else {
  console.log("\n🧹 Step 3: Skipped (first clone, no prior state)");
}

// ── Step 4: Replace repo references in synced files ─────────────────────────

console.log("\n✏️  Step 4: Replace repo references");

for (const relPath of REPLACE_REPO_FILES) {
  const filePath = join(PROJECT_ROOT, relPath);
  replaceRepoRefs(filePath);
}

console.log("  [ok] All repo references replaced in synced files");
console.log(`  synced: ${stats.synced}, skipped: ${stats.skipped}, keep-local: ${stats.keepLocal}, deleted: ${stats.deleted}`);

console.log("\n✅ Sync complete!");
