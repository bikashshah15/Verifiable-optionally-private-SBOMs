import React, { useRef, useState } from "https://esm.sh/react@18";
import { createRoot } from "https://esm.sh/react-dom@18/client";
import htm from "https://esm.sh/htm@3?bundle";

const html = htm.bind(React.createElement);

const sampleRepos = [
    "https://github.com/Koenkk/zigbee2mqtt",
    "https://github.com/thingsboard/thingsboard-gateway",
    "https://github.com/eclipse-leshan/leshan",
    "https://github.com/edgexfoundry/edgex-go",
    "https://github.com/chirpstack/chirpstack",
    "https://github.com/1technophile/OpenMQTTGateway",
    "https://github.com/wled/WLED",
    "https://github.com/contiki-ng/contiki-ng",
    "https://github.com/home-assistant/android",
    "https://github.com/aws/aws-iot-device-sdk-embedded-C",
];

const SHOW_SAMPLE_REPOS = false;

const artifactDescriptions = {
    "Raw SPDX SBOM": "Machine-readable component inventory.",
    "SWID tag": "Software identity metadata for this repository/version.",
    "Combined SPDX+SWID": "Combined inventory and identity artifact.",
    "Publication manifest": "Record of what was generated and published.",
    "Verifiable private record": "SHA-256 verification record without exposing the full SBOM.",
};

const howItWorksSteps = [
    "Clone repo",
    "Generate SPDX SBOM",
    "Create SWID identity tag",
    "Prepare publication record",
    "Publish selected artifacts to Zenodo",
];

const publicationModes = {
    FULL_SBOM_PUBLIC: {
        title: "Full SBOM publication",
        eyebrow: "Publish full artifact set",
        description:
            "Publish the full SPDX SBOM, SWID identity tag, combined SPDX+SWID artifact, publication manifest, and Zenodo record with an assigned DOI.",
        publicSummary:
            "Full component inventory, SWID metadata, combined SPDX+SWID artifact, publication manifest, DOI, timestamp, and Zenodo record.",
        previewTitle: "What will be public",
        previewItems: [
            "Full SPDX SBOM",
            "SWID identity tag",
            "Combined SPDX+SWID artifact",
            "Publication manifest",
            "DOI, timestamp, and Zenodo record",
        ],
        previewNote:
            "Published artifact links are returned after a successful Zenodo publication run.",
    },
    HASH_ONLY_PUBLIC: {
        title: "Verifiable private record",
        eyebrow: "Keep SBOM details private",
        description:
            "Publish a DOI, timestamp, and SHA-256 verification hash without publishing the full SBOM component inventory.",
        publicSummary:
            "DOI, timestamp, SHA-256 verification hash, and publication manifest. The full component inventory is not public.",
        previewTitle: "What will be public",
        previewItems: [
            "DOI",
            "Timestamp",
            "SHA-256 verification hash",
            "Publication manifest",
            "One-time private SBOM download available for 15 minutes",
        ],
        previewNote:
            "The full component inventory is generated but not published.",
    },
};

const workflowSteps = [
    "Validating repository URL",
    "Cloning repository",
    "Generating SBOM",
    "Validating SPDX",
    "Computing SHA-256 hash and metadata",
    "Preparing publication record",
    "Publishing selected public artifacts",
    "Verifying DOI",
    "Removing local workspace",
];

const statusCopy = {
    idle: "Ready to run the publication workflow.",
    running: "Processing the repository and preparing the selected public record.",
    success: "Publication workflow completed.",
    error: "Publication workflow finished with errors.",
};

const stageLabels = {
    LOCAL_GENERATION: "Preparing publication record...",
    DOI_RESERVING: "Preparing publication record...",
    DOI_RESERVED: "Preparing publication record...",
    ARTIFACTS_UPLOADING: "Publishing selected public artifacts...",
    ARTIFACTS_UPLOADED: "Publishing selected public artifacts...",
    RECORD_PUBLISHING: "Publishing selected public artifacts...",
    RECORD_PUBLISHED: "Publishing selected public artifacts...",
    DOI_VERIFYING: "Verifying DOI...",
    DOI_VERIFIED: "DOI verified.",
    FAILED: "Publication failed.",
};

function formatTimestamp(value) {
    if (!value) {
        return "";
    }
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

function stepState(index, activeIndex, status) {
    if (status === "success") {
        return "workflow-step-complete";
    }
    if (activeIndex < 0) {
        return "workflow-step-pending";
    }
    if (index < activeIndex) {
        return "workflow-step-complete";
    }
    if (index === activeIndex) {
        return status === "error" ? "workflow-step-error" : "workflow-step-active";
    }
    return "workflow-step-pending";
}

function workflowMarker(index, state) {
    if (state === "workflow-step-complete") {
        return "✓";
    }
    if (state === "workflow-step-error") {
        return "!";
    }
    return String(index + 1);
}

function friendlyErrorMessage(message) {
    const lower = (message || "").toLowerCase();
    if (
        lower.includes("git url")
        || lower.includes("https://")
        || lower.includes("git clone")
        || lower.includes("prepare repo")
    ) {
        return "This must be a public HTTPS Git repository URL, for example https://github.com/org/repo.";
    }
    return "VOPS could not complete the publication workflow. Check the repository URL and try again.";
}

function App() {
    const resultRef = useRef(null);
    const [gitUrl, setGitUrl] = useState("");
    const [selectedSample, setSelectedSample] = useState(sampleRepos[0]);
    const [publicationMode, setPublicationMode] = useState("FULL_SBOM_PUBLIC");
    const [status, setStatus] = useState("idle");
    const [result, setResult] = useState(null);
    const [error, setError] = useState("");
    const [errorDetail, setErrorDetail] = useState("");
    const [privateDownloadUsed, setPrivateDownloadUsed] = useState(false);

    const canSubmit = gitUrl.trim().length > 8 && status !== "running";
    const submitLabel =
        status === "running"
            ? "Processing..."
            : publicationMode === "HASH_ONLY_PUBLIC"
                ? "Generate Verifiable Private Record"
                : "Generate and Publish SBOM";
    const statusClass =
        status === "running"
            ? "status-running"
            : status === "success"
                ? "status-success"
                : status === "error"
                    ? "status-error"
                    : "status-idle";

    const effectiveMode = result?.publicationMode || publicationMode;
    const modeConfig = publicationModes[effectiveMode] || publicationModes.FULL_SBOM_PUBLIC;

    const handleSample = () => {
        setGitUrl(selectedSample);
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!canSubmit) return;

        setStatus("running");
        setResult(null);
        setError("");
        setErrorDetail("");
        setPrivateDownloadUsed(false);
        requestAnimationFrame(() => {
            resultRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
        });

        try {
            const response = await fetch("/api/sbom", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    gitUrl: gitUrl.trim(),
                    publicationMode,
                }),
            });
            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                if (data && typeof data === "object") {
                    setResult(data);
                }
                throw new Error(data.message || "SBOM generation failed.");
            }

            setResult(data);
            setStatus("success");
        } catch (err) {
            const detail = err.message || "";
            setError(friendlyErrorMessage(detail));
            setErrorDetail(detail);
            setStatus("error");
        }
    };

    const handlePrivateDownload = () => {
        if (!result?.privateSbomDownloadUrl || privateDownloadUsed) {
            return;
        }
        setPrivateDownloadUsed(true);
        window.location.assign(result.privateSbomDownloadUrl);
    };

    const publicationStatus =
        result?.publicationStatus
        || (status === "error" ? "Failed" : status === "running" ? "Publishing" : "Draft");

    const progressMessage = (() => {
        if (status === "running") {
            return effectiveMode === "HASH_ONLY_PUBLIC"
                ? "Preparing a verifiable private record."
                : "Preparing a full public SBOM publication.";
        }
        if (result?.publicationStage === "DOI_VERIFIED") {
            return "DOI verified.";
        }
        if (result?.publicationStage === "FAILED") {
            return "Publication failed.";
        }
        return result?.progressMessage || stageLabels[result?.publicationStage] || "Awaiting execution.";
    })();

    const activeStepIndex = (() => {
        if (status === "success") {
            return workflowSteps.length - 1;
        }
        if (status === "running") {
            return 5;
        }
        switch (result?.publicationStage) {
            case "ARTIFACTS_UPLOADING":
            case "ARTIFACTS_UPLOADED":
            case "RECORD_PUBLISHING":
            case "RECORD_PUBLISHED":
                return 6;
            case "DOI_VERIFYING":
            case "DOI_VERIFIED":
                return 7;
            case "FAILED":
                return 6;
            case "LOCAL_GENERATION":
            case "DOI_RESERVING":
            case "DOI_RESERVED":
                return 5;
            default:
                return -1;
        }
    })();

    const reservedDoi = result?.doi && !result?.zenodoPublished ? result.doi : "";
    const publishedDoi = result?.doi && result?.zenodoPublished ? result.doi : "";
    const recordUrl = result?.zenodoRecordUrl || result?.zenodoDraftUrl || "";
    const manifestUrl = result?.publicPublicationManifestUrl || "";
    const showPrivateDownload =
        status === "success"
        && result
        && effectiveMode === "HASH_ONLY_PUBLIC"
        && result.privateSbomDownloadAvailable === true
        && result.privateSbomDownloadUrl;
    const privateDownloadExpiresAt = formatTimestamp(result?.privateSbomDownloadExpiresAt);

    const publishedArtifactLinks = effectiveMode === "HASH_ONLY_PUBLIC"
        ? [
            { label: "Verifiable private record", url: result?.proofRecordUrl },
            { label: "Publication manifest", url: manifestUrl },
        ].filter((item) => item.url && item.url.trim().length > 0)
        : [
            { label: "Raw SPDX SBOM", url: result?.publicSbomUrl },
            { label: "SWID tag", url: result?.publicSwidUrl },
            { label: "Combined SPDX+SWID", url: result?.publicCombinedSbomUrl },
            { label: "Publication manifest", url: manifestUrl },
        ].filter((item) => item.url && item.url.trim().length > 0);

    return html`
        <div className="page">
            <div className="shell">
                <div className="top">
                    <header className="hero">
                        <span className="brand-mark">VOPS</span>
                        <div>
                            <p className="tagline">Verifiable (optionally private) SBOMs</p>
                            <h1>SBOM publication records for IoT and edge software studies.</h1>
                        </div>
                        <p>
                            VOPS supports the generation of compliant, auditable, dated SBOMs that are easy to locate,
                            cite, and associate with a specific commit of free and open source software. VOPS is designed
                            for users of OSS, particularly IoT, edge, and connected-device software, where an SBOM is
                            needed without increasing the burden on maintainers.
                        </p>
                        <div className="what-panel">
                            <div className="info-label">What this tool does</div>
                            <p>
                                VOPS clones a public repository, generates an SPDX SBOM, creates SWID identity metadata,
                                and publishes either the full SBOM artifact set or a publicly verifiable private record.
                            </p>
                        </div>
                    </header>

                    <div className="grid">
                        <section className="card form-card">
                            <form onSubmit=${handleSubmit}>
                                <div className="field-group">
                                    <label htmlFor="gitUrl">Enter a public Git repository URL</label>
                                    <input
                                        id="gitUrl"
                                        type="text"
                                        placeholder="https://github.com/org/repo"
                                        value=${gitUrl}
                                        onInput=${(event) => setGitUrl(event.target.value)}
                                        autoComplete="off"
                                    />
                                    <p className="hint">
                                        <span className="info-icon">ⓘ</span>
                                        Use a public HTTPS Git repository such as https://github.com/org/repo. Do not use ZIP links,
                                        SSH URLs, private repositories, or ordinary web pages.
                                    </p>

                                    ${SHOW_SAMPLE_REPOS ? html`
                                        <div className="sample-row">
                                            <label className="sample-label" htmlFor="sampleRepo">Sample repository</label>
                                            <div className="sample-controls">
                                                <select
                                                    id="sampleRepo"
                                                    value=${selectedSample}
                                                    onChange=${(event) => setSelectedSample(event.target.value)}
                                                >
                                                    ${sampleRepos.map((repo) => html`
                                                        <option value=${repo}>${repo}</option>
                                                    `)}
                                                </select>
                                                <button className="secondary" type="button" onClick=${handleSample}>
                                                    Use selected sample
                                                </button>
                                            </div>
                                        </div>
                                    ` : null}
                                </div>

                                <div className="field-group">
                                    <div className="section-heading">Publication mode</div>
                                    <div className="mode-grid">
                                        ${Object.entries(publicationModes).map(([value, config]) => {
                                            const selected = publicationMode === value;
                                            return html`
                                                <label className=${"mode-card " + (selected ? "mode-card-active" : "")}>
                                                    <input
                                                        type="radio"
                                                        name="publicationMode"
                                                        value=${value}
                                                        checked=${selected}
                                                        onChange=${() => setPublicationMode(value)}
                                                    />
                                                    <div className="mode-card-header">
                                                        <div className="mode-eyebrow">${config.eyebrow}</div>
                                                        ${selected ? html`
                                                            <span className="selected-chip">
                                                                <span className="selected-check">✓</span>
                                                                Selected
                                                            </span>
                                                        ` : null}
                                                    </div>
                                                    <div className="mode-title">${config.title}</div>
                                                    <p>${config.description}</p>
                                                    <div className="mode-public-summary">
                                                        <strong>What will be public:</strong> ${config.publicSummary}
                                                    </div>
                                                </label>
                                            `;
                                        })}
                                    </div>
                                </div>

                                <div className="field-group">
                                    <div className="section-heading">Public output preview</div>
                                    <div className="preview-panel">
                                        <div className="preview-title">${modeConfig.previewTitle}</div>
                                        <div className="preview-list">
                                            ${modeConfig.previewItems.map((item) => html`
                                                <div className="preview-item">${item}</div>
                                            `)}
                                        </div>
                                        <p className="preview-note">${modeConfig.previewNote}</p>
                                    </div>
                                </div>

                                <p className="run-note">
                                    <span className="info-icon">ⓘ</span>
                                    This may take 30 seconds to a few minutes depending on repository size.
                                </p>

                                <div className="actions">
                                    <button className="primary" type="submit" disabled=${!canSubmit}>
                                        ${submitLabel}
                                    </button>
                                </div>
                            </form>
                        </section>

                        <aside className="card info-card">
                            <div>
                                <div className="info-label">How it works</div>
                                <p className="how-text">${howItWorksSteps.join(" → ")}</p>
                            </div>
                            <div>
                                <div className="info-label">Workflow</div>
                                <div className="info-list">
                                    ${workflowSteps.map((step) => html`<div>${step}</div>`)}
                                </div>
                            </div>
                            <div>
                                <div className="info-label">Selected output</div>
                                <div className="details">
                                    <div><strong>Mode:</strong> ${modeConfig.title}</div>
                                    <div>${modeConfig.previewNote}</div>
                                </div>
                            </div>
                        </aside>
                    </div>
                </div>

                <section ref=${resultRef} className=${"card result-card " + (status === "success" ? "result-card-success" : "")}>
                    <div className="status-row">
                        <span className=${"status-pill " + statusClass}>${status.toUpperCase()}</span>
                        ${status === "running" ? html`<span className="spinner"></span>` : null}
                        <span>${statusCopy[status]}</span>
                    </div>

                    ${status === "success" && result ? html`
                        <div className="success-header">
                            <div>
                                <div className="success-kicker">Success</div>
                                <h2>Publication completed successfully</h2>
                                <p>Next step: copy the DOI or open the Zenodo record to verify the published artifact.</p>
                            </div>
                        </div>
                    ` : null}

                    ${status === "error" ? html`
                        <div className="alert-panel">
                            <strong>${error}</strong>
                            ${errorDetail && errorDetail !== error ? html`<span>${errorDetail}</span>` : null}
                        </div>
                    ` : null}

                    <details
                        className=${"publication-panel workflow-panel " + (status === "success" ? "workflow-panel-compact" : "")}
                        open=${status !== "success"}
                    >
                        <summary>
                            <span>Workflow progress</span>
                            <span>${status === "success" ? "Completed" : progressMessage}</span>
                        </summary>
                        <div className="details workflow-details">
                            <div><strong>Current message:</strong> ${progressMessage}</div>
                            ${result?.publicationStage ? html`<div><strong>Stage:</strong> ${result.publicationStage}</div>` : null}
                            <div><strong>Status:</strong> ${publicationStatus}</div>
                        </div>
                        <div className="workflow-list">
                            ${workflowSteps.map((step, index) => {
                                const state = stepState(index, activeStepIndex, status);
                                return html`
                                    <div className=${"workflow-step " + state}>
                                        <span className="workflow-step-index">${workflowMarker(index, state)}</span>
                                        <span>${step}</span>
                                    </div>
                                `;
                            })}
                        </div>
                    </details>

                    ${result && status === "success" && (result?.doiUrl || recordUrl) ? html`
                        <div className="primary-result-grid">
                            ${result?.doiUrl ? html`
                                <a className="result-action result-action-primary" href=${result.doiUrl} target="_blank" rel="noopener noreferrer">
                                    <span>DOI landing page</span>
                                    <strong>${publishedDoi || reservedDoi || result.doiUrl}</strong>
                                </a>
                            ` : null}
                            ${recordUrl ? html`
                                <a className="result-action" href=${recordUrl} target="_blank" rel="noopener noreferrer">
                                    <span>Zenodo record</span>
                                    <strong>${recordUrl}</strong>
                                </a>
                            ` : null}
                        </div>
                    ` : null}

                    ${result
        ? html`
                            <div className="publication-panel detailed-results-panel">
                                <div className="info-label">Detailed results</div>
                                <div className="details">
                                    <div><strong>Publication mode:</strong> ${result.publicationMode || publicationMode}</div>
                                    ${reservedDoi ? html`<div><strong>Reserved DOI:</strong> ${reservedDoi}</div>` : null}
                                    ${publishedDoi ? html`<div><strong>Published DOI:</strong> ${publishedDoi}</div>` : null}
                                    ${recordUrl
            ? html`
                                            <div>
                                                <strong>Zenodo record:</strong>
                                                <a href=${recordUrl} target="_blank" rel="noopener noreferrer">${recordUrl}</a>
                                            </div>
                                        `
            : null}
                                    ${result?.doiUrl
            ? html`
                                            <div>
                                                <strong>DOI landing page:</strong>
                                                <a href=${result.doiUrl} target="_blank" rel="noopener noreferrer">${result.doiUrl}</a>
                                            </div>
                                        `
            : null}
                                    ${result.generatedAt ? html`<div><strong>Generated:</strong> ${formatTimestamp(result.generatedAt)}</div>` : null}
                                    ${result.commitSha ? html`<div><strong>Commit SHA:</strong> ${result.commitSha}</div>` : null}
                                    ${result.resolvedVersion ? html`<div><strong>Version:</strong> ${result.resolvedVersion}</div>` : null}
                                    ${result.sbomSha256 ? html`<div><strong>SBOM SHA-256:</strong> ${result.sbomSha256}</div>` : null}
                                    ${effectiveMode === "HASH_ONLY_PUBLIC" && result.proofRecordFileName
            ? html`<div><strong>Verifiable private record file:</strong> ${result.proofRecordFileName}</div>`
            : null}
                                    ${effectiveMode === "FULL_SBOM_PUBLIC" && result.fileName
            ? html`<div><strong>SBOM file:</strong> ${result.fileName}</div>`
            : null}
                                    ${effectiveMode === "FULL_SBOM_PUBLIC" && result.swidFileName
            ? html`<div><strong>SWID file:</strong> ${result.swidFileName}</div>`
            : null}
                                    ${effectiveMode === "FULL_SBOM_PUBLIC" && result.combinedSbomFileName
            ? html`<div><strong>Combined file:</strong> ${result.combinedSbomFileName}</div>`
            : null}
                                    ${result.publicationManifestFileName
            ? html`<div><strong>Publication manifest:</strong> ${result.publicationManifestFileName}</div>`
            : null}
                                    ${effectiveMode === "HASH_ONLY_PUBLIC"
            ? html`<div><strong>Public disclosure:</strong> Full SBOM was generated and hashed but not publicly posted.</div>`
            : null}
                                </div>
                            </div>
                        `
        : html`
                            <div className="publication-panel">
                                <div className="info-label">Detailed results</div>
                                <div className="details">Run the workflow to populate DOI, publication, and artifact details.</div>
                            </div>
                        `}

                    ${publishedArtifactLinks.length > 0
        ? html`
                            <div className="publication-panel">
                                <div className="info-label">Published artifact links</div>
                                <div className="artifact-list">
                                    ${publishedArtifactLinks.map((item) => html`
                                        <a className="artifact-link" href=${item.url} target="_blank" rel="noopener noreferrer">
                                            <span>${item.label}</span>
                                            <small>${artifactDescriptions[item.label]}</small>
                                        </a>
                                    `)}
                                </div>
                            </div>
                        `
        : null}

                    ${showPrivateDownload
        ? html`
                            <div className="private-download-panel">
                                <div className="info-label">Private SBOM download</div>
                                <div className="private-download-copy">
                                    <p>
                                        The full SBOM was generated and hashed, but it was not published. It will only
                                        be stored on our system for 15 minutes. You may download the SBOM once for your
                                        own records. After 15 minutes, the SBOM itself will no longer be provided; only
                                        the verifying SHA-256 hash and associated DOI will remain. Please download the
                                        SBOM and make a record of the associated DOI.
                                    </p>
                                </div>
                                <div className="private-expiry-urgent">This link expires in 15 minutes.</div>
                                ${privateDownloadExpiresAt
            ? html`<div className="private-expiry"><strong>Exact expiry:</strong> ${privateDownloadExpiresAt}</div>`
            : null}
                                ${result.privateSbomDownloadMessage
            ? html`<div className="private-download-message">The private SBOM is removed after use or expiration.</div>`
            : null}
                                <div className="private-download-actions">
                                    <button
                                        className="private-download-button"
                                        type="button"
                                        disabled=${privateDownloadUsed}
                                        onClick=${handlePrivateDownload}
                                    >
                                        ${privateDownloadUsed ? "Download used or expired" : "Download private SBOM"}
                                    </button>
                                </div>
                            </div>
                        `
        : null}
                </section>
            </div>
        </div>
    `;
}

createRoot(document.getElementById("app")).render(html`<${App} />`);
