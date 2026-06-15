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
import { dirname, join, resolve } from "node:path";

const UPSTREAM_REPO = "https://github.com/mymx2/starter-gradle.git";
const PROJECT_ROOT = resolve(import.meta.dirname);
const WORKTREE_DIR = join(PROJECT_ROOT, ".worktrees", "starter-gradle");

// Files where mymx2/starter-gradle should be replaced with mymx2/mica-auto-ksp
const REPLACE_REPO_FILES = [
  "git-sync-gitee.yml",
  "publish-snapshot.yml",
  "publish-release.yml",
];

function run(cmd: string, cwd?: string): void {
  console.log(`> ${cmd}`);
  execSync(cmd, { cwd, stdio: "inherit" });
}

function copyDirRecursive(
  src: string,
  dest: string,
  ignoreDirs: Set<string>,
): void {
  mkdirSync(dest, { recursive: true });
  for (const entry of readdirSync(src)) {
    if (ignoreDirs.has(entry)) {
      console.log(`  [skip] ${entry}`);
      continue;
    }
    const srcPath = join(src, entry);
    const destPath = join(dest, entry);
    const stat = statSync(srcPath);
    if (stat.isDirectory()) {
      copyDirRecursive(srcPath, destPath, ignoreDirs);
    } else {
      copyFileSync(srcPath, destPath);
      console.log(`  [copy] ${entry}`);
    }
  }
}

// ── Step 1: Clone or update upstream repo ──────────────────────────────────

console.log("\n📦 Step 1: Clone / update upstream repo");

if (existsSync(join(WORKTREE_DIR, ".git"))) {
  console.log("  Upstream already cloned, pulling latest...");
  run("git pull --ff-only", WORKTREE_DIR);
} else {
  console.log("  Cloning upstream...");
  mkdirSync(join(PROJECT_ROOT, ".worktrees"), { recursive: true });
  run(`git clone ${UPSTREAM_REPO} ${WORKTREE_DIR}`);
}

// ── Step 2: Sync .github/workflows (ignore actions dir) ────────────────────

console.log("\n🔄 Step 2: Sync .github/workflows");

const srcWorkflows = join(WORKTREE_DIR, ".github", "workflows");
const destWorkflows = join(PROJECT_ROOT, ".github", "workflows");

copyDirRecursive(srcWorkflows, destWorkflows, new Set(["actions"]));

// ── Step 3: Replace mymx2/starter-gradle → mymx2/mica-auto-ksp ─────────────

console.log("\n✏️  Step 3: Replace repo references in workflow files");

for (const file of REPLACE_REPO_FILES) {
  const filePath = join(destWorkflows, file);
  if (!existsSync(filePath)) {
    console.log(`  [warn] ${file} not found, skipping`);
    continue;
  }
  const content = readFileSync(filePath, "utf-8");
  const updated = content.replaceAll("mymx2/starter-gradle", "mymx2/mica-auto-ksp");
  if (updated !== content) {
    writeFileSync(filePath, updated, "utf-8");
    console.log(`  [replaced] ${file}`);
  } else {
    console.log(`  [no-change] ${file}`);
  }
}

// ── Step 4: Sync gradle files ──────────────────────────────────────────────

console.log("\n📋 Step 4: Sync gradle files");

const gradleFiles = [
  join("gradle", "libs.versions.toml"),
  join("gradle", "wrapper", "gradle-wrapper.properties"),
];

for (const rel of gradleFiles) {
  const src = join(WORKTREE_DIR, rel);
  const dest = join(PROJECT_ROOT, rel);
  if (!existsSync(src)) {
    console.log(`  [warn] ${rel} not found in upstream, skipping`);
    continue;
  }
  mkdirSync(dirname(dest), { recursive: true });
  copyFileSync(src, dest);
  console.log(`  [copy] ${rel}`);
}

console.log("\n✅ Sync complete!");
