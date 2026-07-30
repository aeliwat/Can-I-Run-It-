(() => {
  const hardwareEl = document.getElementById("hardware");
  const modelsEl = document.getElementById("models");
  const refreshBtn = document.getElementById("refresh");
  const filterBtns = [...document.querySelectorAll(".filter")];
  const addForm = document.getElementById("add-form");
  const addStatus = document.getElementById("add-status");

  let report = null;
  let activeFilter = "ALL";

  async function loadReport() {
    hardwareEl.innerHTML = `<p class="loading">Detecting hardware…</p>`;
    modelsEl.innerHTML = "";
    try {
      const res = await fetch("/api/report", { cache: "no-store" });
      if (!res.ok) {
        throw new Error(`Request failed (${res.status})`);
      }
      report = await res.json();
      if (report.error) {
        throw new Error(report.error);
      }
      render();
    } catch (err) {
      hardwareEl.innerHTML = `<p class="error">${escapeHtml(err.message || "Failed to load report")}</p>`;
      modelsEl.innerHTML = "";
    }
  }

  function render() {
    renderHardware(report.hardware);
    renderModels(report.results);
  }

  function renderHardware(hw) {
    const gpuNames = (hw.gpus || [])
      .map((g) => `${g.name} (${formatGb(g.vramGb)} GB)`)
      .join(" · ");

    hardwareEl.innerHTML = `
      <div class="hw-stat">
        <span class="label">System RAM</span>
        <div class="value">${formatGb(hw.totalRamGb)} <span class="unit">GB</span></div>
      </div>
      <div class="hw-stat">
        <span class="label">Total VRAM</span>
        <div class="value">${formatGb(hw.totalVramGb)} <span class="unit">GB</span></div>
        <div class="detail">${gpuNames || "No discrete GPU detected"}</div>
      </div>
      <div class="hw-stat">
        <span class="label">Runnable now</span>
        <div class="value">${countByStatus(report.results, "OPTIMAL")}<span class="unit"> / ${report.results.length}</span></div>
        <div class="detail">models fit in VRAM (optimal)</div>
      </div>
    `;
  }

  function renderModels(results) {
    const filtered = activeFilter === "ALL"
      ? results
      : results.filter((r) => r.status === activeFilter);

    if (!filtered.length) {
      modelsEl.innerHTML = `<p class="empty">No models match this filter.</p>`;
      return;
    }

    modelsEl.innerHTML = filtered.map((r, i) => {
      const statusClass = r.status.toLowerCase();
      const label = statusLabel(r.status);
      const customTag = r.custom ? `<span class="custom-tag">Custom</span>` : "";
      const removeBtn = r.custom
        ? `<button type="button" class="remove-btn" data-remove="${escapeAttr(r.name)}">Remove</button>`
        : `<span></span>`;
      const tip = r.suggestion && r.status !== "OPTIMAL"
        ? `<div class="model-tip">${escapeHtml(r.suggestion.summary)}</div>`
        : (r.status === "OPTIMAL"
          ? `<div class="model-tip is-ok">Already optimal on this GPU.</div>`
          : "");
      return `
        <article class="model-row is-${statusClass}" style="animation-delay:${i * 40}ms">
          <div class="model-main">
            <div class="model-name">${escapeHtml(r.name)}${customTag}</div>
            <div class="model-meta">${escapeHtml(r.category || "LLM")} · ${formatGb(r.parametersInBillions)}B · ${r.quantizationBits}-bit · need ${formatGb(r.requiredMemoryGb)} GB</div>
            ${tip}
          </div>
          <div class="metric">${r.suggestion ? formatGb(r.suggestion.neededVramGb) : "—"} <span class="unit">GB VRAM for fast</span></div>
          <div class="metric">${r.quantizationBits} <span class="unit">bit</span></div>
          <span class="badge ${statusClass}">${label}</span>
          ${removeBtn}
        </article>
      `;
    }).join("");
  }

  function countByStatus(results, status) {
    return results.filter((r) => r.status === status).length;
  }

  function statusLabel(status) {
    switch (status) {
      case "OPTIMAL": return "Optimal";
      case "SLOW": return "Slow";
      case "INCOMPATIBLE": return "Can't run";
      default: return status;
    }
  }

  function formatGb(n) {
    const num = Number(n);
    if (Number.isNaN(num)) return "—";
    return num.toFixed(1).replace(/\.0$/, "");
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;");
  }

  function escapeAttr(value) {
    return escapeHtml(value).replaceAll("'", "&#39;");
  }

  function setAddStatus(message, kind) {
    addStatus.textContent = message || "";
    addStatus.classList.toggle("is-error", kind === "error");
    addStatus.classList.toggle("is-ok", kind === "ok");
  }

  filterBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      activeFilter = btn.dataset.filter;
      filterBtns.forEach((b) => b.classList.toggle("is-active", b === btn));
      if (report) {
        renderModels(report.results);
      }
    });
  });

  modelsEl.addEventListener("click", async (event) => {
    const btn = event.target.closest("[data-remove]");
    if (!btn) return;
    const name = btn.getAttribute("data-remove");
    if (!name) return;
    if (!window.confirm(`Remove custom model "${name}"?`)) return;

    try {
      const res = await fetch(`/api/models?name=${encodeURIComponent(name)}`, {
        method: "DELETE"
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || `Remove failed (${res.status})`);
      }
      await loadReport();
    } catch (err) {
      setAddStatus(err.message || "Failed to remove model", "error");
    }
  });

  addForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setAddStatus("Saving…", null);

    const form = new FormData(addForm);
    const payload = {
      name: String(form.get("name") || "").trim(),
      parametersInBillions: Number(form.get("parametersInBillions")),
      quantizationBits: Number(form.get("quantizationBits")),
      contextBufferGb: Number(form.get("contextBufferGb") || 1),
      category: String(form.get("category") || "LLM").trim() || "LLM"
    };

    try {
      const res = await fetch("/api/models", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || `Add failed (${res.status})`);
      }
      setAddStatus(`Added “${data.model.name}”.`, "ok");
      addForm.reset();
      addForm.elements.quantizationBits.value = "4";
      addForm.elements.contextBufferGb.value = "1.0";
      addForm.elements.category.value = "LLM";
      await loadReport();
    } catch (err) {
      setAddStatus(err.message || "Failed to add model", "error");
    }
  });

  refreshBtn.addEventListener("click", loadReport);
  loadReport();
})();
