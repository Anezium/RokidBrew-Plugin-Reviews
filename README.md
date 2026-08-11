# RokidBrew Plugin Reviews

This repository is the isolated review workspace for Nexus plugins submitted to
[RokidBrew-Registry](https://github.com/Anezium/RokidBrew-Registry). It runs
entirely on GitHub Actions; there is no review server to deploy or maintain.

When a maintainer adds the `plugin-source-review` label to a Registry pull
request, the automation:

1. authenticates the source repository and release tag declared by the plugin
   descriptor;
2. creates an ephemeral base branch containing the previous released source
   (when one exists) and the matching Rokid Nexus SDK documentation;
3. creates an ephemeral head branch containing the proposed released source;
4. opens a pull request whose diff is the real plugin source change;
5. lets installed review apps review that pull request; and
6. mirrors their summaries and inline findings back to the original Registry
   pull request with permanent links to the upstream commit.

The source snapshot is never built or executed. Generated branches and review
pull requests are closed and deleted when a new Registry revision supersedes
them or when the Registry pull request closes.

## One-time setup

### 1. Cross-repository token

Create one fine-grained personal access token restricted to these repositories:

- `Anezium/RokidBrew-Registry`
- `Anezium/RokidBrew-Plugin-Reviews`

Grant it these repository permissions:

- Actions: read and write
- Contents: read
- Issues: read and write
- Pull requests: read and write

Save it as the Actions secret `ROKIDBREW_REVIEW_TOKEN` in both repositories.
The Registry copy dispatches this repository's workflow. The copy here reads
the Registry PR, explicitly triggers the installed reviewers after applying the
review label, and updates the source-review comment. The workflow's scoped
`GITHUB_TOKEN` owns the generated PR and its disposable branches, so the
cross-repository token does not need `Contents: write`.

### 2. Review apps

Install any combination of the following on this repository:

- **Codex**: connect the repository in Codex settings and enable code reviews.
  The workflow posts `@codex review` after applying the review label, and
  `AGENTS.md` supplies the Nexus review contract.
- **CodeRabbit**: install its GitHub App. `.coderabbit.yaml` scopes reviews to
  generated PRs carrying the `plugin-source-review` label; the workflow posts
  the explicit `@coderabbitai review` command after applying that label.
- **Greptile**: install its GitHub App. `greptile.json` scopes it to the same
  label, points it at the materialized Nexus contracts, and declares
  `Anezium/Rokid-Nexus` as a pattern repository. The workflow posts
  `@greptileai` after applying the label so Greptile cannot miss it during PR
  creation.

CodeRabbit and Greptile can both be installed, but one reviewer plus Codex is
usually less repetitive. The relay accepts all three and clearly identifies
which engine produced each finding.

### 3. Registry trigger

Merge `.github/workflows/request-plugin-source-review.yml` in
RokidBrew-Registry and create the `plugin-source-review` label. Adding that
label starts or refreshes a review. Removing it stops future refreshes; closing
the Registry PR cleans up the generated review branches.

## Local checks

The automation is plain Node.js 22 with no package dependencies:

```bash
node --test
node scripts/plugin-review.mjs inspect \
  --registry Anezium/RokidBrew-Registry \
  --pr 69
```

`inspect` is read-only. It resolves the descriptor, source tag, exact commit,
and Nexus context without creating branches, comments, or pull requests. Set
`REGISTRY_TOKEN` or `GH_TOKEN` when unauthenticated GitHub API limits are too
low.

## Trust boundaries

- The Registry trigger is a `pull_request_target` workflow, but it never checks
  out or executes contributor code.
- Source URLs must be public GitHub repositories, and the release asset URL
  must name the same repository.
- Only regular, bounded text files are copied. Build outputs, binaries, local
  configuration, credentials, signing material, and symbolic links are
  excluded.
- Relay events are accepted only from open generated PRs and recognized Codex,
  CodeRabbit, or Greptile accounts. Stale reviews cannot overwrite a newer
  Registry revision. Reviewer summaries, boilerplate, progress, and quota
  comments stay on the disposable review PR; only inline findings are relayed.
  A completed reviewer with no inline findings is shown as `No findings
  reported`.
- A relayed AI review is advisory. APK provenance, signer, manifest, descriptor,
  and generated-feed checks remain mandatory merge gates in Registry.
