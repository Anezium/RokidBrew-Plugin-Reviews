#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import {
  chmodSync,
  copyFileSync,
  existsSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { basename, dirname, extname, join, relative, resolve, sep } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";

export const REVIEW_LABEL = "plugin-source-review";
export const ORIGIN_COMMENT_MARKER = "<!-- rokidbrew-plugin-source-review -->";
export const REVIEW_MARKER_PREFIX = "<!-- rokidbrew-plugin-review:v1 ";

const EXPECTED_REVIEWERS = ["codex", "coderabbit", "greptile"];
const REVIEWER_LABELS = {
  codex: "Codex",
  coderabbit: "CodeRabbit",
  greptile: "Greptile",
};
const MAX_SOURCE_FILES = 4_000;
const MAX_SOURCE_FILE_BYTES = 2 * 1024 * 1024;
const MAX_SOURCE_TOTAL_BYTES = 40 * 1024 * 1024;
const MAX_RELAY_ITEM_CHARS = 2_500;
const MAX_RELAY_ITEMS_PER_REVIEWER = 6;
const NEXUS_REPOSITORY = "Anezium/Rokid-Nexus";

const EXCLUDED_DIRECTORIES = new Set([
  ".git",
  ".gradle",
  ".idea",
  ".kotlin",
  ".claude",
  ".codex",
  ".cursor",
  ".tmp",
  "build",
  "dist",
  "node_modules",
  "out",
]);
const EXCLUDED_FILENAMES = new Set([
  ".env",
  ".cursorrules",
  "agents.md",
  "claude.md",
  "codex.md",
  "copilot-instructions.md",
  "google-services.json",
  "local.properties",
  "secrets.properties",
]);
const EXCLUDED_EXTENSIONS = new Set([
  ".7z",
  ".aab",
  ".apk",
  ".bin",
  ".class",
  ".der",
  ".dex",
  ".gif",
  ".ico",
  ".jar",
  ".jpeg",
  ".jpg",
  ".jks",
  ".key",
  ".keystore",
  ".p12",
  ".pem",
  ".pfx",
  ".png",
  ".so",
  ".webp",
  ".zip",
]);
const CONTEXT_FILES = [
  "AGENTS.md",
  "README.md",
  "BUSSPEC.md",
  "plugins/AGENTS.md",
  "docs/PLUGIN_SDK.md",
  "docs/PLUGINS.md",
];

function fail(message) {
  throw new Error(message);
}

export function parseArguments(argv) {
  const [command, ...tokens] = argv;
  const values = {};
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index];
    if (!token.startsWith("--")) fail(`Unexpected argument ${JSON.stringify(token)}`);
    const key = token.slice(2);
    const value = tokens[index + 1];
    if (value == null || value.startsWith("--")) fail(`Missing value for --${key}`);
    values[key] = value;
    index += 1;
  }
  return { command, values };
}

export function parseRepositorySlug(value) {
  if (/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(String(value || ""))) {
    return String(value);
  }
  let parsed;
  try {
    parsed = new URL(String(value));
  } catch {
    fail(`Expected a GitHub repository URL, received ${JSON.stringify(value)}`);
  }
  const segments = parsed.pathname.replace(/\.git$/i, "").split("/").filter(Boolean);
  if (parsed.protocol !== "https:" || parsed.hostname.toLowerCase() !== "github.com" ||
      segments.length !== 2 ||
      !segments.every((segment) => /^[A-Za-z0-9_.-]+$/.test(segment))) {
    fail(`Expected https://github.com/<owner>/<repo>, received ${JSON.stringify(value)}`);
  }
  return `${segments[0]}/${segments[1]}`;
}

export function parseReleaseAssetUrl(value) {
  let parsed;
  try {
    parsed = new URL(String(value));
  } catch {
    fail("artifact.url is not a valid URL");
  }
  const segments = parsed.pathname.split("/").filter(Boolean);
  if (parsed.protocol !== "https:" || parsed.hostname.toLowerCase() !== "github.com" ||
      segments.length < 6 || segments[2] !== "releases" || segments[3] !== "download") {
    fail("artifact.url must be a GitHub release asset URL");
  }
  const repository = `${decodeURIComponent(segments[0])}/${decodeURIComponent(segments[1])}`;
  const tag = decodeURIComponent(segments[4]);
  parseRepositorySlug(repository);
  if (!tag) fail("artifact.url release tag is empty");
  return { repository, tag };
}

export function descriptorSource(descriptor, descriptorPath) {
  if (!descriptor || descriptor.kind !== "nexus-plugin") {
    fail(`${descriptorPath} is not a Nexus plugin descriptor`);
  }
  const filenameId = basename(descriptorPath, ".json");
  if (!/^[a-z0-9][a-z0-9-]*$/.test(descriptor.id || "") || descriptor.id !== filenameId) {
    fail(`${descriptorPath} must contain the same safe plugin id as its filename`);
  }
  if (descriptor.nexus?.pluginId !== descriptor.id) {
    fail(`${descriptorPath} nexus.pluginId must equal id`);
  }
  const release = parseReleaseAssetUrl(descriptor.artifact?.url);
  const sourceRepository = parseRepositorySlug(descriptor.sourceUrl);
  if (sourceRepository.toLowerCase() !== release.repository.toLowerCase()) {
    fail(`${descriptorPath} sourceUrl and artifact.url must use the same GitHub repository`);
  }
  return {
    pluginId: descriptor.id,
    pluginName: String(descriptor.name || descriptor.id),
    version: String(descriptor.artifact?.versionName || "unknown"),
    repository: release.repository,
    repositoryUrl: `https://github.com/${release.repository}`,
    tag: release.tag,
  };
}

function encodedPath(value) {
  return String(value).split("/").map(encodeURIComponent).join("/");
}

function apiUrl(endpoint) {
  return `https://api.github.com${endpoint}`;
}

async function githubApi(token, method, endpoint, body) {
  const headers = {
    Accept: "application/vnd.github+json",
    "User-Agent": "RokidBrew-Plugin-Reviews",
    "X-GitHub-Api-Version": "2022-11-28",
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(apiUrl(endpoint), {
    method,
    headers: {
      ...headers,
      ...(body == null ? {} : { "Content-Type": "application/json" }),
    },
    body: body == null ? undefined : JSON.stringify(body),
  });
  if (response.status === 204) return null;
  const text = await response.text();
  let payload = null;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = text;
  }
  if (!response.ok) {
    const detail = typeof payload === "object" && payload?.message ? payload.message : text;
    const error = new Error(`GitHub ${method} ${endpoint} failed (${response.status}): ${detail}`);
    error.status = response.status;
    throw error;
  }
  return payload;
}

async function paginated(token, endpoint) {
  const separator = endpoint.includes("?") ? "&" : "?";
  const results = [];
  for (let page = 1; page <= 20; page += 1) {
    const batch = await githubApi(token, "GET", `${endpoint}${separator}per_page=100&page=${page}`);
    if (!Array.isArray(batch)) fail(`Expected a list from ${endpoint}`);
    results.push(...batch);
    if (batch.length < 100) return results;
  }
  fail(`GitHub pagination limit exceeded for ${endpoint}`);
}

async function repositoryFile(token, repository, filePath, ref) {
  const payload = await githubApi(
    token,
    "GET",
    `/repos/${repository}/contents/${encodedPath(filePath)}?ref=${encodeURIComponent(ref)}`,
  );
  if (payload?.type !== "file" || payload.encoding !== "base64") {
    fail(`Expected ${repository}:${filePath}@${ref} to be a file`);
  }
  return Buffer.from(payload.content.replace(/\s/g, ""), "base64").toString("utf8");
}

async function resolveTagCommit(token, repository, tag) {
  let object = (await githubApi(
    token,
    "GET",
    `/repos/${repository}/git/ref/tags/${encodedPath(tag)}`,
  )).object;
  for (let depth = 0; depth < 6; depth += 1) {
    if (object?.type === "commit" && /^[0-9a-f]{40}$/i.test(object.sha)) {
      return object.sha.toLowerCase();
    }
    if (object?.type !== "tag" || !/^[0-9a-f]{40}$/i.test(object.sha || "")) {
      fail(`Release tag ${repository}@${tag} does not resolve to a Git commit`);
    }
    object = (await githubApi(token, "GET", `/repos/${repository}/git/tags/${object.sha}`)).object;
  }
  fail(`Release tag ${repository}@${tag} has too many nested annotated tags`);
}

async function resolveCommit(token, repository, ref) {
  const commit = await githubApi(
    token,
    "GET",
    `/repos/${repository}/commits/${encodeURIComponent(ref)}`,
  );
  if (!/^[0-9a-f]{40}$/i.test(commit?.sha || "")) fail(`${repository}@${ref} is not a commit`);
  return commit.sha.toLowerCase();
}

export function encodeReviewMarker(metadata) {
  const encoded = Buffer.from(JSON.stringify(metadata), "utf8").toString("base64url");
  return `${REVIEW_MARKER_PREFIX}${encoded} -->`;
}

export function decodeReviewMarker(body) {
  const escaped = REVIEW_MARKER_PREFIX.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = String(body || "").match(new RegExp(`${escaped}([A-Za-z0-9_-]+) -->`));
  if (!match) return null;
  try {
    const metadata = JSON.parse(Buffer.from(match[1], "base64url").toString("utf8"));
    return metadata?.version === 1 ? metadata : null;
  } catch {
    return null;
  }
}

async function resolveSubmission({
  registry,
  prNumber,
  token,
  expectedHead = "",
  requireLabel = true,
}) {
  parseRepositorySlug(registry);
  if (!/^\d+$/.test(String(prNumber))) fail("Pull request number must be numeric");
  const pull = await githubApi(token, "GET", `/repos/${registry}/pulls/${prNumber}`);
  if (pull.state !== "open") fail(`Registry PR #${prNumber} is not open`);
  if (expectedHead && pull.head.sha !== expectedHead) {
    fail(`Registry PR moved from expected ${expectedHead} to ${pull.head.sha}; ignoring stale run`);
  }
  if (requireLabel && !pull.labels?.some((label) => label.name === REVIEW_LABEL)) {
    fail(`Registry PR #${prNumber} no longer has the ${REVIEW_LABEL} label`);
  }

  const files = await paginated(token, `/repos/${registry}/pulls/${prNumber}/files`);
  const descriptors = files.filter((file) =>
    /^plugins-nexus\/[^/]+\.json$/.test(file.filename) &&
    !file.filename.endsWith(".template.json") && file.status !== "removed");
  if (descriptors.length !== 1) {
    fail(`Expected exactly one added or changed Nexus plugin descriptor, found ${descriptors.length}`);
  }
  const changed = descriptors[0];
  const descriptorText = await repositoryFile(token, registry, changed.filename, pull.head.sha);
  let descriptor;
  try {
    descriptor = JSON.parse(descriptorText);
  } catch {
    fail(`${changed.filename} is not valid JSON`);
  }
  const proposed = descriptorSource(descriptor, changed.filename);
  proposed.commit = await resolveTagCommit(token, proposed.repository, proposed.tag);

  let previous = null;
  if (changed.status !== "added") {
    const previousPath = changed.previous_filename || changed.filename;
    const previousText = await repositoryFile(token, registry, previousPath, pull.base.sha);
    let previousDescriptor;
    try {
      previousDescriptor = JSON.parse(previousText);
    } catch {
      fail(`${previousPath} at the Registry base is not valid JSON`);
    }
    previous = descriptorSource(previousDescriptor, previousPath);
    previous.commit = await resolveTagCommit(token, previous.repository, previous.tag);
    if (previous.pluginId !== proposed.pluginId) fail("Plugin id changes are not reviewable as updates");
  }

  return {
    version: 1,
    origin: {
      repository: registry,
      prNumber: Number(prNumber),
      url: pull.html_url,
      title: pull.title,
      headSha: pull.head.sha,
      baseSha: pull.base.sha,
    },
    descriptorPath: changed.filename,
    plugin: {
      id: proposed.pluginId,
      name: proposed.pluginName,
      version: proposed.version,
    },
    previous,
    proposed,
    reviewers: EXPECTED_REVIEWERS,
  };
}

function git(args, { cwd = process.cwd(), allowFailure = false } = {}) {
  const result = spawnSync("git", args, {
    cwd,
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
  });
  if (result.error) fail(`git ${args[0]} failed: ${result.error.message}`);
  if (result.status !== 0 && !allowFailure) {
    fail(`git ${args.join(" ")} failed: ${(result.stderr || result.stdout || result.status).trim()}`);
  }
  return result;
}

function cloneCommit(repository, commit, destination, tag = null) {
  mkdirSync(destination, { recursive: true });
  git(["init", "--quiet"], { cwd: destination });
  git(["remote", "add", "origin", `https://github.com/${repository}.git`], { cwd: destination });
  const ref = tag ? `refs/tags/${tag}` : commit;
  git(["fetch", "--quiet", "--depth=1", "--filter=blob:none", "origin", ref], {
    cwd: destination,
  });
  git(["checkout", "--quiet", "--detach", commit], { cwd: destination });
}

function walkFiles(root, visitor, relativeDirectory = "") {
  const directory = join(root, relativeDirectory);
  for (const entry of readdirSync(directory, { withFileTypes: true }).sort((a, b) =>
    a.name.localeCompare(b.name))) {
    const relativePath = relativeDirectory ? join(relativeDirectory, entry.name) : entry.name;
    if (entry.isSymbolicLink()) continue;
    if (entry.isDirectory()) {
      if (!EXCLUDED_DIRECTORIES.has(entry.name.toLowerCase())) walkFiles(root, visitor, relativePath);
    } else if (entry.isFile()) {
      visitor(relativePath);
    }
  }
}

function shouldExcludeFile(relativePath) {
  const name = basename(relativePath).toLowerCase();
  if (EXCLUDED_FILENAMES.has(name) || name.startsWith(".env.")) return true;
  if (EXCLUDED_EXTENSIONS.has(extname(name))) return true;
  return relativePath.replaceAll("\\", "/").toLowerCase() === ".github/copilot-instructions.md";
}

export function copySourceSnapshot(sourceRoot, destinationRoot) {
  rmSync(destinationRoot, { recursive: true, force: true });
  mkdirSync(destinationRoot, { recursive: true });
  let files = 0;
  let bytes = 0;
  const copied = [];
  walkFiles(sourceRoot, (relativePath) => {
    if (shouldExcludeFile(relativePath)) return;
    const sourcePath = join(sourceRoot, relativePath);
    const size = statSync(sourcePath).size;
    if (size > MAX_SOURCE_FILE_BYTES) return;
    const content = readFileSync(sourcePath);
    if (content.includes(0)) return;
    files += 1;
    bytes += size;
    if (files > MAX_SOURCE_FILES) fail(`Source snapshot exceeds ${MAX_SOURCE_FILES} text files`);
    if (bytes > MAX_SOURCE_TOTAL_BYTES) fail("Source snapshot exceeds 40 MiB of text files");
    const destinationPath = join(destinationRoot, relativePath);
    mkdirSync(dirname(destinationPath), { recursive: true });
    copyFileSync(sourcePath, destinationPath);
    chmodSync(destinationPath, lstatSync(sourcePath).mode & 0o777);
    copied.push(relativePath.replaceAll("\\", "/"));
  });
  if (copied.length === 0) fail("Source repository contains no reviewable text files");
  return { files: copied.length, bytes, paths: copied };
}

export function detectNexusSdkTag(sourceRoot) {
  const matches = new Set();
  walkFiles(sourceRoot, (relativePath) => {
    const normalized = relativePath.replaceAll("\\", "/");
    if (!/(?:^|\/)(?:build\.gradle(?:\.kts)?|libs\.versions\.toml|gradle\.properties)$/i.test(normalized)) {
      return;
    }
    const filePath = join(sourceRoot, relativePath);
    if (statSync(filePath).size > MAX_SOURCE_FILE_BYTES) return;
    const text = readFileSync(filePath, "utf8");
    const pattern = /com\.github\.Anezium\.Rokid-Nexus:bus-client:([A-Za-z0-9._-]+)/gi;
    for (const match of text.matchAll(pattern)) matches.add(match[1]);
  });
  if (matches.size > 1) fail(`Source declares multiple Nexus SDK versions: ${[...matches].join(", ")}`);
  return [...matches][0] || null;
}

async function attachNexusContext(metadata, token, temporaryRoot) {
  const proposedSource = join(temporaryRoot, "proposed-source");
  cloneCommit(
    metadata.proposed.repository,
    metadata.proposed.commit,
    proposedSource,
    metadata.proposed.tag,
  );
  const sdkTag = detectNexusSdkTag(proposedSource);
  let contextRef = "main";
  let contextCommit;
  if (sdkTag) {
    try {
      contextCommit = await resolveTagCommit(token, NEXUS_REPOSITORY, sdkTag);
      contextRef = sdkTag;
    } catch (error) {
      console.warn(`Could not resolve declared Nexus SDK ${sdkTag}; using current main: ${error.message}`);
    }
  }
  if (!contextCommit) contextCommit = await resolveCommit(token, NEXUS_REPOSITORY, "main");
  const nexusSource = join(temporaryRoot, "nexus-source");
  cloneCommit(NEXUS_REPOSITORY, contextCommit, nexusSource, contextRef === "main" ? null : contextRef);
  metadata.context = {
    repository: NEXUS_REPOSITORY,
    declaredSdkTag: sdkTag,
    ref: contextRef,
    commit: contextCommit,
  };
  return { proposedSource, nexusSource };
}

function materializeContext(worktree, metadata, nexusSource) {
  const contextRoot = join(worktree, ".review-context");
  const nexusRoot = join(contextRoot, "nexus");
  rmSync(contextRoot, { recursive: true, force: true });
  for (const file of CONTEXT_FILES) {
    const source = join(nexusSource, ...file.split("/"));
    if (!existsSync(source)) fail(`Nexus context file is missing: ${file}`);
    const destination = join(nexusRoot, ...file.split("/"));
    mkdirSync(dirname(destination), { recursive: true });
    copyFileSync(source, destination);
  }
  const previous = metadata.previous
    ? `${metadata.previous.repository}@${metadata.previous.tag} (${metadata.previous.commit})`
    : "no previous Registry release";
  writeFileSync(join(contextRoot, "README.md"), `# Generated review context

- Origin: ${metadata.origin.url}
- Plugin: ${metadata.plugin.name} (${metadata.plugin.id}) ${metadata.plugin.version}
- Previous source: ${previous}
- Proposed source: ${metadata.proposed.repository}@${metadata.proposed.tag} (${metadata.proposed.commit})
- Nexus context: ${metadata.context.repository}@${metadata.context.ref} (${metadata.context.commit})

The pull-request diff contains only the proposed plugin source change. Files in
this directory are trusted reference material placed on the base branch and are
not part of the candidate change.
`, "utf8");
  writeFileSync(join(contextRoot, "review.json"), `${JSON.stringify(metadata, null, 2)}\n`, "utf8");
}

function safeBranchPart(value, maximum = 42) {
  const cleaned = String(value).toLowerCase().replace(/[^a-z0-9-]+/g, "-")
    .replace(/^-+|-+$/g, "").slice(0, maximum);
  return cleaned || "plugin";
}

function reviewBranches(metadata) {
  const key = `pr-${metadata.origin.prNumber}-${safeBranchPart(metadata.plugin.id)}-${metadata.proposed.commit.slice(0, 10)}`;
  return { base: `review-base/${key}`, head: `review-head/${key}` };
}

function generatedPullBody(metadata) {
  const previous = metadata.previous
    ? `[${metadata.previous.repository}@${metadata.previous.tag}](https://github.com/${metadata.previous.repository}/tree/${metadata.previous.commit})`
    : "No previous release (the full source is added)";
  return `${encodeReviewMarker(metadata)}

## Released plugin source review

This generated PR reviews the source behind [Registry PR #${metadata.origin.prNumber}](${metadata.origin.url}), not the Registry JSON diff.

| | Exact source |
|---|---|
| Plugin | \`${metadata.plugin.id}\` ${metadata.plugin.version} |
| Previous | ${previous} |
| Proposed | [${metadata.proposed.repository}@${metadata.proposed.tag}](https://github.com/${metadata.proposed.repository}/tree/${metadata.proposed.commit}) |
| Nexus contract | [${metadata.context.ref}](https://github.com/${metadata.context.repository}/tree/${metadata.context.commit}) |

Review changed files under \`candidate/${metadata.plugin.id}/\`. The base branch
already contains the previous source and the exact Nexus context, so comments
land on the real release diff. This PR is disposable review infrastructure and
must not be merged.
`;
}

async function ensureReviewLabel(token, repository) {
  try {
    await githubApi(token, "GET", `/repos/${repository}/labels/${encodeURIComponent(REVIEW_LABEL)}`);
  } catch (error) {
    if (error.status !== 404) throw error;
    await githubApi(token, "POST", `/repos/${repository}/labels`, {
      name: REVIEW_LABEL,
      color: "5319e7",
      description: "Generated Rokid Nexus plugin source review",
    });
  }
}

async function matchingReviewPulls(token, reviewRepository, registry, prNumber, state = "open") {
  const pulls = await paginated(token, `/repos/${reviewRepository}/pulls?state=${state}`);
  return pulls.filter((pull) => {
    const metadata = decodeReviewMarker(pull.body);
    return metadata?.origin?.repository === registry && metadata.origin.prNumber === Number(prNumber);
  });
}

async function deleteReviewBranch(token, repository, branch) {
  if (!/^review-(?:base|head)\/[a-z0-9-]+$/.test(branch || "")) return;
  try {
    await githubApi(
      token,
      "DELETE",
      `/repos/${repository}/git/refs/heads/${encodedPath(branch)}`,
    );
  } catch (error) {
    if (error.status !== 404 && error.status !== 422) throw error;
  }
}

async function closeGeneratedReviews(reviewToken, branchToken, reviewRepository, registry, prNumber) {
  const pulls = await matchingReviewPulls(
    reviewToken,
    reviewRepository,
    registry,
    prNumber,
    "all",
  );
  let closed = 0;
  for (const pull of pulls) {
    if (pull.state === "open") {
      await githubApi(reviewToken, "PATCH", `/repos/${reviewRepository}/pulls/${pull.number}`, {
        state: "closed",
      });
      closed += 1;
    }
    await deleteReviewBranch(branchToken, reviewRepository, pull.head.ref);
    await deleteReviewBranch(branchToken, reviewRepository, pull.base.ref);
  }
  return closed;
}

async function upsertOriginComment(token, metadata, body) {
  const comments = await paginated(
    token,
    `/repos/${metadata.origin.repository}/issues/${metadata.origin.prNumber}/comments`,
  );
  const existing = comments.find((comment) => String(comment.body || "").startsWith(ORIGIN_COMMENT_MARKER));
  if (existing) {
    await githubApi(
      token,
      "PATCH",
      `/repos/${metadata.origin.repository}/issues/comments/${existing.id}`,
      { body },
    );
  } else {
    await githubApi(
      token,
      "POST",
      `/repos/${metadata.origin.repository}/issues/${metadata.origin.prNumber}/comments`,
      { body },
    );
  }
}

function pendingOriginComment(metadata, reviewPull) {
  return `${ORIGIN_COMMENT_MARKER}
## Nexus plugin source review

The Registry JSON points to **${metadata.plugin.name} ${metadata.plugin.version}** at
[\`${metadata.proposed.repository}@${metadata.proposed.commit.slice(0, 12)}\`](https://github.com/${metadata.proposed.repository}/tree/${metadata.proposed.commit}).

The real source diff is ready in [review PR #${reviewPull.number}](${reviewPull.html_url}), with
the Nexus \`${metadata.context.ref}\` contract attached to its base branch.

${metadata.reviewers.map((reviewer) => `- **${REVIEWER_LABELS[reviewer]}:** waiting for the installed reviewer`).join("\n")}

Reviews are advisory; APK provenance, signer, manifest, descriptor, and feed checks remain authoritative.
`;
}

async function prepareReview(options) {
  const registryToken = process.env.REGISTRY_TOKEN;
  const reviewToken = process.env.REVIEW_TOKEN;
  const reviewBranchToken = process.env.REVIEW_BRANCH_TOKEN;
  if (!registryToken) fail("REGISTRY_TOKEN is required");
  if (!reviewToken) fail("REVIEW_TOKEN is required");
  if (!reviewBranchToken) fail("REVIEW_BRANCH_TOKEN is required");
  const metadata = await resolveSubmission({
    registry: options.registry,
    prNumber: options.pr,
    token: registryToken,
    expectedHead: options["expected-head"] || "",
  });
  const reviewRepository = parseRepositorySlug(options["review-repository"]);
  metadata.reviewRepository = reviewRepository;
  metadata.branches = reviewBranches(metadata);
  await closeGeneratedReviews(
    reviewToken,
    reviewBranchToken,
    reviewRepository,
    metadata.origin.repository,
    metadata.origin.prNumber,
  );

  const temporaryRoot = mkdtempSync(join(tmpdir(), "rokidbrew-plugin-review-"));
  const worktree = join(temporaryRoot, "review-worktree");
  let basePushed = false;
  let headPushed = false;
  let reviewPull = null;
  try {
    const { proposedSource, nexusSource } = await attachNexusContext(
      metadata,
      registryToken,
      temporaryRoot,
    );
    git(["worktree", "add", "--quiet", "-b", metadata.branches.base, worktree, "HEAD"]);
    git(["config", "user.name", "github-actions[bot]"], { cwd: worktree });
    git(["config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], {
      cwd: worktree,
    });
    materializeContext(worktree, metadata, nexusSource);
    if (metadata.previous) {
      const previousSource = join(temporaryRoot, "previous-source");
      cloneCommit(
        metadata.previous.repository,
        metadata.previous.commit,
        previousSource,
        metadata.previous.tag,
      );
      copySourceSnapshot(previousSource, join(worktree, "candidate", metadata.plugin.id));
    }
    const basePaths = [".review-context"];
    if (existsSync(join(worktree, "candidate"))) basePaths.push("candidate");
    git(["add", "-f", "--", ...basePaths], { cwd: worktree });
    git(["commit", "--quiet", "-m", `Prepare ${metadata.plugin.id} review base`], { cwd: worktree });
    git(["push", "--quiet", "origin", `HEAD:refs/heads/${metadata.branches.base}`], { cwd: worktree });
    basePushed = true;

    git(["switch", "--quiet", "-c", metadata.branches.head], { cwd: worktree });
    copySourceSnapshot(proposedSource, join(worktree, "candidate", metadata.plugin.id));
    git(["add", "-f", "-A", "--", `candidate/${metadata.plugin.id}`], { cwd: worktree });
    const staged = git(["diff", "--cached", "--quiet"], { cwd: worktree, allowFailure: true });
    if (staged.status === 0) fail("The proposed release has no source diff from the current Registry release");
    if (staged.status !== 1) fail("Could not inspect the staged plugin source diff");
    git(["commit", "--quiet", "-m", `Review ${metadata.plugin.id} ${metadata.plugin.version}`], {
      cwd: worktree,
    });
    git(["push", "--quiet", "origin", `HEAD:refs/heads/${metadata.branches.head}`], { cwd: worktree });
    headPushed = true;

    reviewPull = await githubApi(reviewToken, "POST", `/repos/${reviewRepository}/pulls`, {
      title: `[Source review] ${metadata.plugin.name} ${metadata.plugin.version}`,
      head: metadata.branches.head,
      base: metadata.branches.base,
      body: generatedPullBody(metadata),
      draft: false,
      maintainer_can_modify: false,
    });
    await ensureReviewLabel(reviewToken, reviewRepository);
    await githubApi(reviewToken, "POST", `/repos/${reviewRepository}/issues/${reviewPull.number}/labels`, {
      labels: [REVIEW_LABEL],
    });
    await upsertOriginComment(registryToken, metadata, pendingOriginComment(metadata, reviewPull));
    console.log(`Created ${reviewPull.html_url}`);
  } catch (error) {
    if (reviewPull) {
      await githubApi(reviewToken, "PATCH", `/repos/${reviewRepository}/pulls/${reviewPull.number}`, {
        state: "closed",
      }).catch(() => {});
    }
    if (headPushed) {
      await deleteReviewBranch(reviewBranchToken, reviewRepository, metadata.branches.head);
    }
    if (basePushed) {
      await deleteReviewBranch(reviewBranchToken, reviewRepository, metadata.branches.base);
    }
    throw error;
  } finally {
    if (existsSync(worktree)) {
      git(["worktree", "remove", "--force", worktree], { allowFailure: true });
    }
    git(["worktree", "prune"], { allowFailure: true });
    rmSync(temporaryRoot, { recursive: true, force: true });
  }
}

export function classifyReviewer(login) {
  const normalized = String(login || "").toLowerCase().replace(/\[bot\]$/, "");
  if (normalized.includes("coderabbit")) return "coderabbit";
  if (normalized.includes("greptile")) return "greptile";
  if (normalized.includes("codex") || normalized.includes("chatgpt")) return "codex";
  return null;
}

function cleanRelayBody(value) {
  const body = String(value || "").replaceAll(ORIGIN_COMMENT_MARKER, "").trim();
  if (body.length <= MAX_RELAY_ITEM_CHARS) return body;
  return `${body.slice(0, MAX_RELAY_ITEM_CHARS)}\n\n… truncated; open the review workspace for the full text.`;
}

export function sourceLineUrl(metadata, comment) {
  const prefix = `candidate/${metadata.plugin.id}/`;
  const filePath = String(comment.path || "").replaceAll("\\", "/");
  if (!filePath.startsWith(prefix) || filePath.includes("../")) return null;
  const isPrevious = (comment.side === "LEFT" || comment.start_side === "LEFT") && metadata.previous;
  const source = isPrevious ? metadata.previous : metadata.proposed;
  const upstreamPath = filePath.slice(prefix.length);
  if (!upstreamPath) return null;
  const line = Number(comment.line || comment.original_line || comment.start_line || 0);
  const startLine = Number(comment.start_line || line);
  const anchor = line > 0
    ? (startLine > 0 && startLine !== line ? `#L${startLine}-L${line}` : `#L${line}`)
    : "";
  return `https://github.com/${source.repository}/blob/${source.commit}/${encodedPath(upstreamPath)}${anchor}`;
}

function relayItem({ type, body, url, path = null, sourceUrl = null, state = null }) {
  return { type, body: cleanRelayBody(body), url, path, sourceUrl, state };
}

async function collectReviewerOutput(token, reviewRepository, reviewPr, metadata) {
  const [reviews, inlineComments, issueComments] = await Promise.all([
    paginated(token, `/repos/${reviewRepository}/pulls/${reviewPr}/reviews`),
    paginated(token, `/repos/${reviewRepository}/pulls/${reviewPr}/comments`),
    paginated(token, `/repos/${reviewRepository}/issues/${reviewPr}/comments`),
  ]);
  const output = Object.fromEntries(metadata.reviewers.map((reviewer) => [reviewer, []]));
  for (const review of reviews) {
    const reviewer = classifyReviewer(review.user?.login);
    if (!reviewer || !output[reviewer]) continue;
    output[reviewer].push(relayItem({
      type: "summary",
      body: review.body || `Review submitted with state ${review.state}.`,
      url: review.html_url,
      state: review.state,
    }));
  }
  for (const comment of inlineComments) {
    const reviewer = classifyReviewer(comment.user?.login);
    if (!reviewer || !output[reviewer]) continue;
    output[reviewer].push(relayItem({
      type: "inline",
      body: comment.body,
      url: comment.html_url,
      path: comment.path,
      sourceUrl: sourceLineUrl(metadata, comment),
    }));
  }
  for (const comment of issueComments) {
    const reviewer = classifyReviewer(comment.user?.login);
    if (!reviewer || !output[reviewer]) continue;
    output[reviewer].push(relayItem({
      type: "summary",
      body: comment.body,
      url: comment.html_url,
    }));
  }
  for (const reviewer of metadata.reviewers) {
    const seen = new Set();
    output[reviewer] = output[reviewer].filter((item) => {
      const key = `${item.type}\0${item.body}\0${item.path || ""}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    }).sort((left, right) => Number(right.type === "summary") - Number(left.type === "summary"))
      .slice(0, MAX_RELAY_ITEMS_PER_REVIEWER);
  }
  return output;
}

function renderReviewerSection(reviewer, items) {
  const label = REVIEWER_LABELS[reviewer] || reviewer;
  if (!items.length) return `### ${label}\n\nWaiting for this installed reviewer.`;
  const content = items.map((item, index) => {
    if (item.type === "inline") {
      const location = item.sourceUrl
        ? `[\`${item.path}\`](${item.sourceUrl})`
        : `\`${item.path || "source"}\``;
      return `#### Finding ${index + 1} — ${location}\n\n${item.body}\n\n[Open review thread](${item.url})`;
    }
    const state = item.state ? ` (${String(item.state).toLowerCase()})` : "";
    return `<details open><summary>Review output${state}</summary>\n\n${item.body}\n\n[Open original output](${item.url})\n\n</details>`;
  }).join("\n\n");
  return `### ${label}\n\n${content}`;
}

export function renderRelayedComment(metadata, reviewPull, output) {
  const sections = metadata.reviewers.map((reviewer) =>
    renderReviewerSection(reviewer, output[reviewer] || [])).join("\n\n");
  return `${ORIGIN_COMMENT_MARKER}
## Nexus plugin source review

Reviewed **${metadata.plugin.name} ${metadata.plugin.version}** at
[\`${metadata.proposed.repository}@${metadata.proposed.commit.slice(0, 12)}\`](https://github.com/${metadata.proposed.repository}/tree/${metadata.proposed.commit})
against the materialized Nexus \`${metadata.context.ref}\` contract.

[Open the complete review workspace](${reviewPull.html_url})

${sections}

---
Reviews are advisory; APK provenance, signer, manifest, descriptor, and feed checks remain authoritative.
`;
}

async function relayReview(options) {
  const registryToken = process.env.REGISTRY_TOKEN;
  const reviewToken = process.env.REVIEW_TOKEN;
  if (!registryToken) fail("REGISTRY_TOKEN is required");
  if (!reviewToken) fail("REVIEW_TOKEN is required");
  const reviewRepository = parseRepositorySlug(options["review-repository"]);
  if (!/^\d+$/.test(String(options["review-pr"] || ""))) fail("Review PR number must be numeric");
  const reviewPull = await githubApi(
    reviewToken,
    "GET",
    `/repos/${reviewRepository}/pulls/${options["review-pr"]}`,
  );
  const metadata = decodeReviewMarker(reviewPull.body);
  if (!metadata || metadata.reviewRepository !== reviewRepository) {
    console.log("Ignoring a pull request that was not generated by this automation");
    return;
  }
  if (reviewPull.state !== "open" ||
      !reviewPull.labels?.some((label) => label.name === REVIEW_LABEL) ||
      reviewPull.head.ref !== metadata.branches.head || reviewPull.base.ref !== metadata.branches.base) {
    console.log("Ignoring a closed or structurally invalid generated review");
    return;
  }
  const origin = await githubApi(
    registryToken,
    "GET",
    `/repos/${metadata.origin.repository}/pulls/${metadata.origin.prNumber}`,
  );
  if (origin.state !== "open" || origin.head.sha !== metadata.origin.headSha) {
    console.log("Ignoring a stale review for an older Registry revision");
    return;
  }
  const output = await collectReviewerOutput(
    reviewToken,
    reviewRepository,
    reviewPull.number,
    metadata,
  );
  await upsertOriginComment(
    registryToken,
    metadata,
    renderRelayedComment(metadata, reviewPull, output),
  );
  console.log(`Relayed review output to ${metadata.origin.url}`);
}

async function cleanupReview(options) {
  const reviewToken = process.env.REVIEW_TOKEN;
  const reviewBranchToken = process.env.REVIEW_BRANCH_TOKEN;
  if (!reviewToken) fail("REVIEW_TOKEN is required");
  if (!reviewBranchToken) fail("REVIEW_BRANCH_TOKEN is required");
  const reviewRepository = parseRepositorySlug(options["review-repository"]);
  const count = await closeGeneratedReviews(
    reviewToken,
    reviewBranchToken,
    reviewRepository,
    parseRepositorySlug(options.registry),
    options.pr,
  );
  console.log(`Closed ${count} generated review pull request(s)`);
}

async function inspectReview(options) {
  const token = process.env.REGISTRY_TOKEN || process.env.GH_TOKEN || "";
  const metadata = await resolveSubmission({
    registry: options.registry,
    prNumber: options.pr,
    token,
    expectedHead: options["expected-head"] || "",
    requireLabel: false,
  });
  const temporaryRoot = mkdtempSync(join(tmpdir(), "rokidbrew-plugin-inspect-"));
  try {
    await attachNexusContext(metadata, token, temporaryRoot);
    console.log(JSON.stringify(metadata, null, 2));
  } finally {
    rmSync(temporaryRoot, { recursive: true, force: true });
  }
}

async function main() {
  const { command, values } = parseArguments(process.argv.slice(2));
  if (command === "prepare") return prepareReview(values);
  if (command === "relay") return relayReview(values);
  if (command === "cleanup") return cleanupReview(values);
  if (command === "inspect") return inspectReview(values);
  fail("Usage: plugin-review.mjs <prepare|relay|cleanup|inspect> [options]");
}

const isEntrypoint = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isEntrypoint) {
  main().catch((error) => {
    console.error(`::error::${error.stack || error.message || error}`);
    process.exitCode = 1;
  });
}
