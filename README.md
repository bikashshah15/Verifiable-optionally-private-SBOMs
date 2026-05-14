# v-ops

Verifiable (optionally private) SBOMs

## Short Overview

v-ops generates verifiable, optionally private SBOM publication records for public source code repositories.

The application accepts a public HTTPS Git repository URL, clones the repository, generates an SPDX JSON SBOM, validates basic SPDX structure, creates a deterministic SWID XML tag, and prepares publication artifacts. Depending on the selected publication mode, it can publish a full DOI-backed SBOM artifact set to Zenodo or publish only a hash-based proof record that shows an SBOM existed for a specific repository and commit.

SBOM publication records matter because they make software supply chain evidence easier to cite, verify, and compare. A DOI-backed record gives a stable publication target. The SBOM hash, commit SHA, SWID tag, proof record, and publication manifest provide provenance and integrity signals. The hash-only workflow supports public verification without exposing the full component list.

The application is intended for public source repositories only. Inputs must be HTTPS Git repository URLs such as `https://github.com/org/repo`. Private repositories, SSH URLs, ZIP files, and arbitrary webpages are not valid inputs.

## Key Features

- Public HTTPS Git repository input.
- SPDX JSON SBOM generation.
- Syft as the default SBOM generator.
- Optional Trivy support when `sbom.generator=trivy` is configured.
- Basic SPDX JSON validation for required structural fields.
- Deterministic SWID XML generation for repository, commit, SBOM hash, and generation metadata.
- Combined SPDX + SWID artifact generation for full-public publication mode.
- DOI-backed Zenodo publication workflow.
- `HASH_ONLY_PUBLIC` proof-record mode for public proof without publishing the full component list.
- One-time private SBOM download for hash-only mode.
- Local filesystem artifact output.
- Static browser UI served by Spring Boot.
- No separate Node, Vite, or frontend build step.
- No database; runtime state is generated files, temporary clone workspaces, in-memory private download tickets, and optional Zenodo publication state.

## Architecture Overview

v-ops is a Spring Boot MVC application with a static browser frontend and a single SBOM generation API. React 18 and `htm` are loaded in the browser from `esm.sh`; there is no separate frontend build pipeline.

Major components:

- Static frontend: `src/main/resources/static/index.html`, `app.js`, and `app.css`.
- Spring MVC API layer: `SbomController` exposes `POST /api/sbom` and the private one-time download endpoint.
- Git workspace: `GitWorkspace` performs non-interactive shallow HTTPS clones under `output/work`.
- SBOM generator: `SyftSbomGenerator` by default; `TrivySbomGenerator` when configured.
- SBOM validator: `SbomValidator` checks that the SPDX JSON output exists and has required SPDX fields.
- SWID service: `SwidService` and `SwidXmlGenerator` create deterministic SWID XML.
- Publication planner: `ArtifactPublicationPlanner` determines which artifacts are created and published for each mode.
- Proof record builder: `ProofRecordBuilder` creates hash-only JSON proof artifacts.
- Publication manifest builder: `PublicationManifestBuilder` records generated, requested, and published artifacts.
- Zenodo publication service: reserves DOI metadata, uploads selected artifacts, optionally publishes records, and verifies DOI state.
- Local output directories: `output/sboms`, `output/swid`, `output/sbom-swid`, `output/publication`, and `output/work`.

```text
User Browser
  -> Spring Boot Static UI
  -> POST /api/sbom
  -> Git clone
  -> Syft/Trivy SBOM generation
  -> SPDX validation
  -> SWID/proof/manifest generation
  -> Optional Zenodo DOI publication
  -> Result shown in UI
```

## Repository Structure

```text
src/main/java/com/sbom/publicationrecord
  Spring Boot application, API, SBOM pipeline, SWID, publication, Zenodo, batch, and report code.

src/main/resources/static
  Browser UI served directly by Spring Boot.

src/main/resources/application.yml
  Base application configuration. The default Spring profile is `sandbox`.

src/main/resources/application-sandbox.yml
  Sandbox Zenodo profile configuration.

src/main/resources/application-prod.yml
  Production Zenodo profile configuration.

src/main/resources/repos.txt
  Batch-mode repository list.

src/test/java/com/sbom/publicationrecord
  Controller, service, publication mode, manifest, Git workspace, batch, Spring context, and Zenodo metadata/profile tests.

output/
  Local generated artifacts and temporary clone workspaces.

target/
  Maven build output.
```

## Prerequisites

- Java 17 or newer.
- Maven wrapper included in this repository (`./mvnw`).
- `git` available on the host.
- Syft installed at the configured `sbom.syft.path` when using the default generator.
- Trivy installed at the configured `sbom.trivy.path` when `sbom.generator=trivy`.
- A Zenodo or Zenodo Sandbox account and token if Zenodo publication is enabled.

Do not commit real access tokens. Use placeholders such as `ZENODO_ACCESS_TOKEN` and `ZENODO_SANDBOX_ACCESS_TOKEN` in documentation, scripts, and examples.

## Installing Syft

Install Syft using the official Anchore Syft documentation for your operating system, then confirm the configured executable path is correct.

Useful checks:

```bash
syft version
which syft
```

By default, v-ops expects:

```yaml
sbom:
  syft:
    path: /usr/local/bin/syft
```

If Syft is elsewhere, update configuration or pass an override:

```bash
java -jar target/publication-record-0.0.1-SNAPSHOT.jar \
  --sbom.syft.path=/path/to/syft
```

Syft project documentation: `https://github.com/anchore/syft`

## Running Locally

Build the application:

```bash
./mvnw clean package
```

Run the packaged Spring Boot jar:

```bash
java -jar target/publication-record-0.0.1-SNAPSHOT.jar
```

Open:

```text
http://localhost:8080
```

The artifact name comes from `pom.xml`:

- Maven artifactId: `publication-record`
- Version: `0.0.1-SNAPSHOT`
- Expected jar: `target/publication-record-0.0.1-SNAPSHOT.jar`

## Running With Spring Profiles

The base configuration sets the default Spring profile to `sandbox`.

### Sandbox

Zenodo Sandbox is for testing and creates test DOI records.

```bash
export ZENODO_SANDBOX_ACCESS_TOKEN="..."
SPRING_PROFILES_ACTIVE=sandbox java -jar target/publication-record-0.0.1-SNAPSHOT.jar
```

The sandbox profile reads `ZENODO_SANDBOX_ACCESS_TOKEN`, falling back to `ZENODO_ACCESS_TOKEN` if the sandbox-specific variable is unset.

### Production

Production Zenodo creates real public records and real DOI metadata. Verify the title, creators, artifacts, publication mode, and publication setting before publishing.

```bash
export ZENODO_ACCESS_TOKEN="..."
SPRING_PROFILES_ACTIVE=prod java -jar target/publication-record-0.0.1-SNAPSHOT.jar
```

For first production tests, override `zenodo.autoPublish=false` so the Zenodo record remains a draft:

```bash
SPRING_PROFILES_ACTIVE=prod java -jar target/publication-record-0.0.1-SNAPSHOT.jar \
  --zenodo.autoPublish=false
```

Security note: the sandbox and production profile files read tokens from environment variables. Do not print, copy into documentation, or commit real token values. Rotate any token that has already been exposed.

## Configuration

Main configuration keys in the current codebase:

| Key | Purpose |
| --- | --- |
| `sbom.outputDir` | Directory for generated raw SBOM files and `run-report.csv`. Default: `${user.dir}/output/sboms`. |
| `sbom.workDir` | Temporary Git clone workspace. Default: `${user.dir}/output/work`. |
| `sbom.generator` | SBOM generator selection. `syft` by default; `trivy` supported. |
| `sbom.format` | SBOM format passed to the generator and validator. Default: `spdx-json`. |
| `sbom.syft.path` | Syft executable path. |
| `sbom.trivy.path` | Trivy executable path. |
| `swid.enabled` | Enables or disables SWID XML generation. Default: `true`. |
| `swid.outputDir` | Directory for SWID XML files. Default: `${user.dir}/output/swid`. |
| `sbomSwid.outputDir` | Directory for combined SPDX + SWID artifacts. Default: `${user.dir}/output/sbom-swid`. |
| `git.depth` | Git clone depth. Default: `1`. |
| `zenodo.enabled` | Enables Zenodo DOI and artifact publication workflow. |
| `zenodo.baseUrl` | Zenodo API base URL, usually `https://sandbox.zenodo.org` or `https://zenodo.org`. |
| `zenodo.autoPublish` | When `true`, publishes the Zenodo draft after upload and verifies DOI state. When `false`, leaves the deposition as a draft. |
| `zenodo.reserveDoi` | Sends `prereserve_doi` metadata when creating the deposition. |
| `zenodo.uploadRawSbom` | Allows raw SBOM upload in `FULL_SBOM_PUBLIC` mode. |
| `zenodo.uploadSwid` | Allows SWID XML upload in `FULL_SBOM_PUBLIC` mode. |
| `zenodo.uploadCombinedSbom` | Allows combined SPDX + SWID upload in `FULL_SBOM_PUBLIC` mode. |
| `zenodo.uploadPublicationManifest` | Allows publication manifest upload. |
| `zenodo.manifestOutputDir` | Directory for proof records, publication manifests, and `zenodo-publication-index.jsonl`. Default: `${user.dir}/output/publication`. |
| `zenodo.titlePrefix` | Prefix used for Zenodo record titles. |
| `zenodo.creators` | Creator metadata included in Zenodo deposits, in citation order. |
| `app.publicBaseUrl` | Public base URL placeholder from `APP_PUBLIC_BASE_URL`; currently not required by the relative private download endpoint. |
| `app.batch.enabled` | Enables startup batch processing from `src/main/resources/repos.txt`. Default: `false`. |

The Zenodo access token is required when `zenodo.enabled=true`. Use a placeholder such as `ZENODO_ACCESS_TOKEN` or `ZENODO_SANDBOX_ACCESS_TOKEN` and provide the actual value through environment variables, command-line overrides, or a secret manager.

## API Documentation

### `POST /api/sbom`

Request body:

```json
{
  "gitUrl": "https://github.com/org/repo",
  "publicationMode": "FULL_SBOM_PUBLIC"
}
```

Supported `publicationMode` values:

- `FULL_SBOM_PUBLIC`
- `HASH_ONLY_PUBLIC`

If `publicationMode` is omitted, the API defaults to `FULL_SBOM_PUBLIC`.

### Example: Full Public SBOM

```bash
curl -X POST http://localhost:8080/api/sbom \
  -H "Content-Type: application/json" \
  -d '{
    "gitUrl": "https://github.com/anchore/syft",
    "publicationMode": "FULL_SBOM_PUBLIC"
  }'
```

### Example: Hash-Only Public Proof

```bash
curl -X POST http://localhost:8080/api/sbom \
  -H "Content-Type: application/json" \
  -d '{
    "gitUrl": "https://github.com/anchore/syft",
    "publicationMode": "HASH_ONLY_PUBLIC"
  }'
```

### Response Fields

The response is represented by `SbomResponse`. Null fields are omitted from JSON.

| Field | Meaning |
| --- | --- |
| `status` | `OK`, `FAILED`, or `ERROR` depending on controller path. |
| `message` | Human-readable result or error message. |
| `fileName` | Raw SPDX JSON SBOM file name. |
| `format` | Configured SBOM format, usually `spdx-json`. |
| `generatedAt` | Generation timestamp. |
| `publicationMode` | Effective publication mode. |
| `sbomSha256` | SHA-256 hash of the generated SBOM. |
| `commitSha` | Resolved repository HEAD commit SHA. |
| `resolvedVersion` | `git describe --tags --always --abbrev=7` result, with commit fallback. |
| `swidTagId` | Deterministic SWID tag ID. |
| `swidFileName` | Generated SWID XML file name. |
| `combinedSbomFileName` | Combined SPDX + SWID artifact name when created. |
| `proofRecordFileName` | Hash-only proof record artifact name when created. |
| `doi` | Reserved or published DOI when available. |
| `doiUrl` | DOI landing URL when available. |
| `zenodoRecordId` | Zenodo deposition or record ID. |
| `zenodoConceptRecid` | Zenodo concept record ID. |
| `zenodoRecordUrl` | Zenodo record URL. |
| `doiReservedAt` | Timestamp when DOI reservation was observed locally. |
| `doiRegisteredObservedAt` | Timestamp when DOI verification was observed after publish. |
| `publicationManifestFileName` | Publication manifest artifact name. |
| `zenodoPublished` | `true` after a successful auto-publish and DOI verification path. |
| `zenodoDraftUrl` | Draft deposition URL when available. |
| `publicationStage` | Workflow stage such as `LOCAL_GENERATION`, `DOI_RESERVED`, `ARTIFACTS_UPLOADED`, `DOI_VERIFIED`, or `FAILED`. |
| `publicationStatus` | UI-facing status: `Draft`, `Publishing`, `Published`, or `Failed`. |
| `progressMessage` | UI-facing workflow progress message. |
| `publicSbomUrl` | Public Zenodo file URL for the raw SBOM when published. |
| `publicSwidUrl` | Public Zenodo file URL for the SWID XML when published. |
| `publicCombinedSbomUrl` | Public Zenodo file URL for the combined artifact when published. |
| `proofRecordUrl` | Public Zenodo file URL for the proof record when published. |
| `publicPublicationManifestUrl` | Public Zenodo file URL for the publication manifest when published. |
| `rawSbomPublished` | Whether the raw SBOM was publicly published. |
| `combinedArtifactPublished` | Whether the combined SPDX + SWID artifact was publicly published. |
| `privateSbomDownloadUrl` | One-time private SBOM download URL for `HASH_ONLY_PUBLIC` mode when available. |
| `privateSbomDownloadExpiresAt` | Expiry timestamp for the one-time private SBOM download. |
| `privateSbomDownloadAvailable` | Present as `true` when a private SBOM download is available. |
| `privateSbomDownloadMessage` | Explanation for the one-time private SBOM download. |

### Private SBOM Download Endpoint

`HASH_ONLY_PUBLIC` mode creates a temporary one-time private download link when the full SBOM file is preserved locally:

```text
GET /api/sbom/private-download/{token}
```

The link expires after 15 minutes by default, can be used once, and deletes the private SBOM file after a successful download. Expired links also trigger best-effort file deletion.

## Frontend Usage

1. Open `http://localhost:8080` in a browser.
2. Read the top explanation of full-public versus proof-only publication.
3. Enter a public HTTPS Git repository URL.
4. Or choose one of the sample repositories from the sample selector.
5. Choose `FULL_SBOM_PUBLIC` or `HASH_ONLY_PUBLIC`.
6. Submit and wait for processing. Large repositories can take several minutes.
7. Review the DOI, Zenodo links, artifact explanations, commit SHA, SBOM hash, and file names.
8. In `HASH_ONLY_PUBLIC` mode, use the private SBOM download link promptly if it is shown.

Private repositories, SSH URLs, ZIP files, and random webpages are not valid Git repository inputs.

## Output Artifacts

Default local output directories:

```text
output/sboms
  Raw generated SPDX JSON SBOM files and run-report.csv.

output/swid
  Generated SWID XML files.

output/sbom-swid
  Combined SPDX + SWID JSON artifacts.

output/publication
  Proof records, publication manifests, and zenodo-publication-index.jsonl.

output/work
  Temporary Git clone workspace.
```

`output/sboms/run-report.csv` records run timestamp, repository, status, details, SBOM file, SBOM SHA-256, SWID tag ID, and SWID file name.

When Zenodo auto-publish succeeds, the service deletes local copies of uploaded artifacts. In `HASH_ONLY_PUBLIC` mode, the raw SBOM is preserved only long enough to support the one-time private download, then it is deleted after use or expiry. `output/work` is a temporary clone workspace and can be deleted when no run is active.

## Publication Modes and Privacy

### `FULL_SBOM_PUBLIC`

Publishes the full SBOM and related artifacts publicly.

Expected public artifacts:

- Raw SPDX JSON SBOM.
- SWID XML tag.
- Combined SPDX + SWID artifact.
- Publication manifest.

The SBOM component list is public. This mode is appropriate for open publication, citation, compliance evidence, reproducible research, and cases where public component disclosure is acceptable.

### `HASH_ONLY_PUBLIC`

Generates the full SBOM, computes its SHA-256 hash, and does not publicly publish the full component list.

Expected public artifacts:

- Proof record.
- Publication manifest.

The public record proves that an SBOM existed for a specific repository and commit. The proof record includes provenance metadata such as repository URL, commit SHA, resolved version, SBOM hash, generator, SWID tag ID, DOI metadata, and a statement that the full SBOM was intentionally withheld from public publication.

This mode is appropriate when the user wants public proof without exposing the full dependency or component inventory. The current code also supports a one-time private download link for the generated full SBOM. That link is temporary, single-use, and deletes the local SBOM after successful download.

## Zenodo Production Caution

Zenodo Sandbox is for testing. Production Zenodo creates real public records and real DOI metadata.

Before publishing to production:

- Verify the publication mode.
- Verify title and creator metadata.
- Verify which artifacts will be public.
- Verify whether `zenodo.autoPublish` is enabled.
- Use `zenodo.autoPublish=false` for first production tests.
- Confirm the access token belongs to the intended Zenodo environment.

The production profile defaults `zenodo.autoPublish` to `false`. Set `ZENODO_AUTO_PUBLISH=true` or pass `--zenodo.autoPublish=true` only when you intend to publish immediately.

## Creators and Citation Metadata

Current intended Zenodo creators:

- Shah, Bikash - University of North Carolina at Charlotte.
- Camp, L. Jean - University of North Carolina at Charlotte.

Zenodo citation order follows the configured creator order.

## Testing

Run tests with:

```bash
./mvnw test
```

Current test areas include:

- Controller behavior and request validation.
- Publication mode behavior.
- One-time private SBOM download behavior.
- Artifact publication planning.
- Publication manifest building.
- Git workspace behavior.
- Batch runner behavior.
- Spring context loading.
- Zenodo metadata and profile configuration.

## Batch Mode

Batch mode is supported by `BatchSbomCommandLine` and `BatchSbomRunner`.

Enable it with:

```bash
java -jar target/publication-record-0.0.1-SNAPSHOT.jar \
  --app.batch.enabled=true
```

Repository input comes from:

```text
src/main/resources/repos.txt
```

The current `repos.txt` contains one public repository URL. Empty lines and lines starting with `#` are ignored.

Batch mode is local-only in the current implementation. It clones each repository, generates and validates an SPDX JSON SBOM, computes the SBOM hash, creates SWID XML, creates a combined SPDX + SWID artifact when SWID is available, writes `run-report.csv`, and deletes the temporary Git workspace. It does not call the Zenodo publication service, does not create DOI records, and does not apply the API publication modes.

## Troubleshooting

### Syft Not Found

Confirm Syft is installed and `sbom.syft.path` points to the executable:

```bash
which syft
syft version
```

Override the path if needed:

```bash
java -jar target/publication-record-0.0.1-SNAPSHOT.jar \
  --sbom.syft.path=/path/to/syft
```

### Git Not Found

Install `git` and confirm it is available:

```bash
git --version
```

### Invalid Repository URL

The API validates that `gitUrl` starts with `https://` and contains no spaces. A URL can still fail later if `git clone` cannot clone it.

### Private Repository Not Accepted

Git runs non-interactively with prompts disabled. Private repositories, repositories requiring authentication, and SSH URLs are not supported by this workflow.

### Zenodo Token Missing or Invalid

When `zenodo.enabled=true`, a token is required. Use the correct token for the selected Zenodo environment and avoid mixing Sandbox tokens with production Zenodo.

### Zenodo Sandbox vs Production Mismatch

Sandbox uses `https://sandbox.zenodo.org`. Production uses `https://zenodo.org`. Tokens and records are environment-specific.

### Long Runtime for Large Repositories

Large repositories and dependency-heavy projects can take several minutes to clone and scan. The SBOM generator command timeout is currently 10 minutes.

### Shallow Clone Tag or Version Fallback

The default clone depth is `1`. `git describe --tags --always --abbrev=7` may not see full tag history in a shallow clone. In that case, the code falls back to a short commit SHA.

### DOI Not Created

A DOI may be absent when Zenodo is disabled, the token is missing or invalid, DOI reservation fails, artifact upload fails, auto-publish is disabled, or DOI verification fails.

## Security Notes

- Do not commit real tokens.
- Rotate any token that has been exposed.
- Do not submit proprietary or private repositories.
- The service accepts public HTTPS Git repositories only.
- The app has no authentication by default.
- If deploying publicly, add access control, HTTPS, rate limiting, request size controls, operational monitoring, and secret management.
- Avoid logging sensitive artifact contents.
- Treat generated SBOMs as potentially sensitive because they can reveal dependency and component inventory.
- Keep real profile token values in environment variables, untracked local config, or a secret manager before shared development or deployment.

## Limitations

- Public HTTPS Git repositories only.
- No database.
- One API workflow at a time in `SbomService` because generation is guarded by a `ReentrantLock`.
- Syft or Trivy must be installed on the host.
- Shallow clone depth can limit version and tag detection.
- Zenodo availability, credentials, selected profile, and `autoPublish` control publication success.
- SBOM validation is minimal SPDX structural validation, not full semantic correctness or license/compliance verification.
- Private download tickets are in memory, so they do not survive application restart.
- The static frontend depends on browser-loaded React and `htm` from `esm.sh`.

## Research/Study Context

v-ops supports research on verifiable SBOM publication records. It can be used in usability studies that compare full-public SBOM publication with hash-only proof publication and study how reviewers interpret DOI-backed artifacts, provenance metadata, and privacy-preserving publication records.

## License

License: not specified yet.
