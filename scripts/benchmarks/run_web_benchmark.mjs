import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import { spawn, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function parseArgs(argv) {
  const parsed = {};
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith("--")) continue;
    const key = token.slice(2);
    const next = argv[i + 1];
    if (!next || next.startsWith("--")) {
      parsed[key] = "true";
    } else {
      parsed[key] = next;
      i += 1;
    }
  }
  return parsed;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function chromeVersion(chromePath) {
  const result = spawnSync(chromePath, ["--product-version"], { encoding: "utf8" });
  if (result.status !== 0) {
    throw new Error(`Failed to query Chrome version from ${chromePath}: ${result.stderr || result.stdout}`);
  }
  return `Chrome ${result.stdout.trim()}`;
}

function killChromeProcessTree(chrome, signal) {
  if (!chrome?.pid) return false;
  try {
    process.kill(-chrome.pid, signal);
    return true;
  } catch {
    try {
      process.kill(chrome.pid, signal);
      return true;
    } catch {
      return false;
    }
  }
}

async function terminateChrome(chrome, userDataDir) {
  if (!chrome) return;

  let exited = false;
  const waitForExit = new Promise((resolve) => {
    chrome.once("exit", () => {
      exited = true;
      resolve();
    });
  });

  killChromeProcessTree(chrome, "SIGTERM");
  await Promise.race([waitForExit, sleep(2000)]);

  if (!exited) {
    killChromeProcessTree(chrome, "SIGKILL");
    await Promise.race([waitForExit, sleep(1000)]);
  }

  if (process.platform === "linux" && userDataDir) {
    spawnSync("pkill", ["-f", userDataDir], { stdio: "ignore" });
  }
}

function serveStaticFile(rootDir, requestPath, response) {
  const urlPath = requestPath === "/" ? "/index.html" : requestPath;
  const relativePath = decodeURIComponent(urlPath.replace(/^\/+/, ""));
  const candidateBases = [
    rootDir,
    path.join(rootDir, "processedResources", "js", "main"),
    path.join(rootDir, "kotlin-webpack", "js", "productionExecutable"),
  ];
  const resolvedPath = candidateBases
    .map((baseDir) => path.resolve(baseDir, relativePath))
    .find((candidate) =>
      candidate.startsWith(path.resolve(rootDir)) &&
      fs.existsSync(candidate) &&
      !fs.statSync(candidate).isDirectory()
    );

  if (!resolvedPath) {
    response.writeHead(404);
    response.end("Not found");
    return;
  }

  const extension = path.extname(resolvedPath);
  const contentType = {
    ".html": "text/html; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".wasm": "application/wasm",
    ".json": "application/json; charset=utf-8",
    ".map": "application/json; charset=utf-8",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".svg": "image/svg+xml",
  }[extension] || "application/octet-stream";

  response.writeHead(200, { "Content-Type": contentType });
  fs.createReadStream(resolvedPath).pipe(response);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const distDir = path.resolve(args["dist-dir"]);
  const rawDir = path.resolve(args["raw-dir"]);
  const scene = args["scene"];
  const chromePath = args["chrome-path"] || "/opt/google/chrome/google-chrome";
  const repeatCount = Number.parseInt(args["repeat-count"] || "3", 10);
  const page = args["page"] || "index.html";
  const timeoutMs = Number.parseInt(args["timeout-ms"] || "180000", 10);
  const headlessTimeoutMs = Number.parseInt(args["headless-timeout-ms"] || "30000", 10);

  if (!fs.existsSync(distDir)) {
    throw new Error(`Build directory does not exist: ${distDir}`);
  }
  if (!scene) {
    throw new Error("--scene is required");
  }

  fs.mkdirSync(rawDir, { recursive: true });

  let pendingResolve = null;
  let pendingReject = null;

  const server = http.createServer((request, response) => {
    const requestUrl = new URL(request.url, "http://127.0.0.1");
    if (request.method === "POST" && requestUrl.pathname === "/__benchmark_report__") {
      let body = "";
      request.setEncoding("utf8");
      request.on("data", (chunk) => {
        body += chunk;
      });
      request.on("end", () => {
        try {
          const payload = JSON.parse(body);
          if (payload && payload.error) {
            pendingReject?.(new Error(`${payload.scene || scene}: ${payload.error}`));
          } else {
            pendingResolve?.(payload);
          }
          response.writeHead(200, { "Content-Type": "text/plain; charset=utf-8" });
          response.end("ok");
        } catch (error) {
          pendingReject?.(error);
          response.writeHead(400, { "Content-Type": "text/plain; charset=utf-8" });
          response.end("invalid payload");
        } finally {
          pendingResolve = null;
          pendingReject = null;
        }
      });
      return;
    }

    serveStaticFile(distDir, requestUrl.pathname, response);
  });

  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => resolve());
  });

  const address = server.address();
  const port = typeof address === "object" && address ? address.port : 0;
  const chromeVersionLabel = chromeVersion(chromePath);

  async function runAttempt(repeatIndex, headless, attemptTimeoutMs) {
    const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), `materia-web-bench-${scene}-${repeatIndex}-`));
    const maxDurationMs = Math.max(5000, attemptTimeoutMs - 3000);
    const url = `http://127.0.0.1:${port}/${page}?benchmark=1&repeatIndex=${repeatIndex}` +
      `&reportUrl=${encodeURIComponent("/__benchmark_report__")}` +
      `&maxDurationMs=${maxDurationMs}`;

    const chromeArgs = [
      "--enable-unsafe-webgpu",
      "--enable-features=Vulkan,UseSkiaRenderer,UnsafeWebGPU",
      "--use-angle=vulkan",
      "--disable-dev-shm-usage",
      "--disable-background-timer-throttling",
      "--disable-backgrounding-occluded-windows",
      "--disable-renderer-backgrounding",
      "--run-all-compositor-stages-before-draw",
      "--window-size=1920,1080",
      "--no-first-run",
      "--no-default-browser-check",
      `--user-data-dir=${userDataDir}`,
    ];
    if (headless) {
      chromeArgs.push("--headless=new");
    } else {
      chromeArgs.push("--ozone-platform=x11");
      chromeArgs.push("--new-window");
    }
    chromeArgs.push(url);

    let stderr = "";
    let stdout = "";
    const chrome = spawn(chromePath, chromeArgs, {
      cwd: __dirname,
      detached: true,
      stdio: ["ignore", "pipe", "pipe"],
    });
    chrome.stdout.on("data", (chunk) => {
      stdout += chunk.toString();
    });
    chrome.stderr.on("data", (chunk) => {
      stderr += chunk.toString();
    });

    try {
      const payload = await new Promise((resolve, reject) => {
        pendingResolve = resolve;
        pendingReject = reject;
        const timer = setTimeout(() => {
          reject(new Error(`Timed out waiting for browser benchmark result after ${attemptTimeoutMs}ms`));
        }, attemptTimeoutMs);

        const wrap = (handler) => (value) => {
          clearTimeout(timer);
          handler(value);
        };
        pendingResolve = wrap(resolve);
        pendingReject = wrap(reject);
      });

      payload.environment = payload.environment || {};
      payload.environment.browser_version = chromeVersionLabel;
      payload.environment.notes = Array.from(
        new Set([...(payload.environment.notes || []), headless ? "Headless Chrome automation" : "Windowed Chrome fallback"])
      );
      return payload;
    } finally {
      pendingResolve = null;
      pendingReject = null;
      await terminateChrome(chrome, userDataDir);
      fs.rmSync(userDataDir, { recursive: true, force: true });
      if (stderr.trim()) {
        process.stderr.write(stderr);
      }
      if (stdout.trim()) {
        process.stdout.write(stdout);
      }
    }
  }

  try {
    for (let repeatIndex = 1; repeatIndex <= repeatCount; repeatIndex += 1) {
      let payload;
      try {
        payload = await runAttempt(repeatIndex, true, headlessTimeoutMs);
      } catch (headlessError) {
        console.warn(`Headless Chrome benchmark failed for ${scene} repeat ${repeatIndex}: ${headlessError.message}`);
        payload = await runAttempt(repeatIndex, false, timeoutMs);
      }

      const outputPath = path.join(rawDir, `${scene}-web-repeat${repeatIndex}.json`);
      fs.writeFileSync(outputPath, JSON.stringify(payload, null, 2));
      console.log(`Wrote ${outputPath}`);
    }
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

main().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exitCode = 1;
});
