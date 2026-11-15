// SPDX-License-Identifier: GPL-3.0-or-later
"use strict";
const button = document.querySelector("#download");
const status = document.querySelector("#status");
button.addEventListener("click", async () => {
  button.disabled = true;
  const hash = async (bytes) => Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)), b => b.toString(16).padStart(2,"0")).join("");
  try {
    const manifestUrl = new URL(button.dataset.manifest, location.href);
    const response = await fetch(manifestUrl);
    if (!response.ok) throw new Error("The download manifest could not be loaded.");
    const manifest = await response.json();
    if (!Number.isSafeInteger(manifest.archive_bytes) || manifest.archive_bytes < 1 || manifest.archive_bytes > 100*1024*1024 || !Array.isArray(manifest.parts) || manifest.parts.length > 10 || !/^[a-z0-9.-]+$/.test(manifest.archive_file)) throw new Error("The download manifest is invalid.");
    const chunks = [];
    let total = 0;
    for (const [index, part] of manifest.parts.entries()) {
      if (!/^[a-z0-9.-]+$/.test(part.file) || !Number.isSafeInteger(part.bytes) || part.bytes < 1 || part.bytes > 20*1024*1024) throw new Error("A source part is invalid.");
      status.textContent = `Downloading part ${index+1} of ${manifest.parts.length}…`;
      const partResponse = await fetch(new URL(part.file, manifestUrl));
      if (!partResponse.ok) throw new Error("A source part could not be downloaded.");
      const bytes = await partResponse.arrayBuffer();
      total += bytes.byteLength;
      if (bytes.byteLength !== part.bytes || total > manifest.archive_bytes || await hash(bytes) !== part.sha256) throw new Error("A source part failed its checksum. Please retry.");
      chunks.push(bytes);
    }
    const blob = new Blob(chunks, {type:"application/gzip"});
    if (total !== manifest.archive_bytes || await hash(await blob.arrayBuffer()) !== manifest.archive_sha256) throw new Error("The assembled source archive failed its checksum.");
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a"); link.href = url; link.download = manifest.archive_file; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
    status.textContent = "Source archive verified. Your browser download is ready.";
  } catch (error) {
    status.textContent = `${error.message || "Download failed."} You can also use the separate parts and manual instructions below.`;
  } finally { button.disabled = false; }
});
