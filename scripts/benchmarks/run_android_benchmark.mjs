import fs from "node:fs";
import path from "node:path";
import { spawn, spawnSync } from "node:child_process";

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

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    ...options,
  });
  if (result.error) throw result.error;
  return result;
}

function adb(adbPath, deviceId, args, options = {}) {
  const scopedArgs = deviceId ? ["-s", deviceId, ...args] : args;
  return run(adbPath, scopedArgs, options);
}

function listDevices(adbPath) {
  const result = run(adbPath, ["devices"]);
  return result.stdout
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("List of devices attached"))
    .map((line) => line.split(/\s+/)[0])
    .filter(Boolean);
}

async function ensureDevice(args) {
  run(args.adb, ["start-server"]);
  let devices = listDevices(args.adb);
  if (devices.length > 0) {
    return devices[0];
  }

  console.log(`Starting emulator ${args.avd}...`);
  const emulator = spawn(args.emulator, ["-avd", args.avd, "-gpu", "host", "-no-boot-anim", "-no-snapshot-save"], {
    detached: true,
    stdio: "ignore",
  });
  emulator.unref();

  const deadline = Date.now() + 240_000;
  while (Date.now() < deadline) {
    await sleep(2_000);
    devices = listDevices(args.adb);
    if (devices.length > 0) {
      const deviceId = devices[0];
      await waitForBootCompleted(args.adb, deviceId);
      return deviceId;
    }
  }

  throw new Error(`Timed out waiting for emulator ${args.avd} to appear in adb devices`);
}

async function waitForBootCompleted(adbPath, deviceId) {
  const deadline = Date.now() + 240_000;
  while (Date.now() < deadline) {
    await sleep(2_000);
    const result = adb(adbPath, deviceId, ["shell", "getprop", "sys.boot_completed"]);
    if (result.status === 0 && result.stdout.trim() === "1") {
      return;
    }
  }
  throw new Error(`Timed out waiting for ${deviceId} to finish booting`);
}

function currentLogcat(adbPath, deviceId) {
  const result = adb(adbPath, deviceId, ["logcat", "-d", "-v", "raw", "MateriaBenchmark:I", "AndroidRuntime:E", "*:S"]);
  return result.stdout;
}

async function waitForBenchmarkResult(adbPath, deviceId, scene, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    await sleep(2_000);
    const logs = currentLogcat(adbPath, deviceId);
    const resultLine = logs.split("\n").find((line) => line.includes("MATERIA_BENCHMARK_RESULT:"));
    if (resultLine) {
      const payload = resultLine.slice(resultLine.indexOf("MATERIA_BENCHMARK_RESULT:") + "MATERIA_BENCHMARK_RESULT:".length).trim();
      return JSON.parse(payload);
    }

    const resultPartLines = logs
      .split("\n")
      .filter((line) => line.includes("MATERIA_BENCHMARK_RESULT_PART:"));
    if (resultPartLines.length > 0) {
      const parts = new Map();
      let expectedPartCount = null;

      for (const line of resultPartLines) {
        const payload = line.slice(line.indexOf("MATERIA_BENCHMARK_RESULT_PART:") + "MATERIA_BENCHMARK_RESULT_PART:".length).trim();
        const match = payload.match(/^(\d+)\/(\d+):(.*)$/);
        if (!match) {
          continue;
        }
        const partIndex = Number.parseInt(match[1], 10);
        const partCount = Number.parseInt(match[2], 10);
        const partPayload = match[3];
        expectedPartCount = expectedPartCount ?? partCount;
        parts.set(partIndex, partPayload);
      }

      if (expectedPartCount && parts.size === expectedPartCount) {
        let combinedPayload = "";
        for (let index = 1; index <= expectedPartCount; index += 1) {
          const partPayload = parts.get(index);
          if (typeof partPayload !== "string") {
            combinedPayload = "";
            break;
          }
          combinedPayload += partPayload;
        }
        if (combinedPayload) {
          return JSON.parse(combinedPayload);
        }
      }
    }

    const failureLine = logs.split("\n").find((line) => line.includes("MATERIA_BENCHMARK_FAILURE:"));
    if (failureLine) {
      const message = failureLine.slice(failureLine.indexOf("MATERIA_BENCHMARK_FAILURE:") + "MATERIA_BENCHMARK_FAILURE:".length).trim();
      throw new Error(`${scene}: ${message}`);
    }

    const fatalLine = logs.split("\n").find((line) => line.includes("FATAL EXCEPTION"));
    if (fatalLine) {
      throw new Error(`Android runtime crash while waiting for ${scene} benchmark:\n${logs}`);
    }
  }

  throw new Error(`Timed out waiting for ${scene} benchmark result`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const adbPath = args["adb"] || "/home/yousef/Android/Sdk/platform-tools/adb";
  const emulatorPath = args["emulator"] || "/home/yousef/Android/Sdk/emulator/emulator";
  const avd = args["avd"] || "Pixel_9_Pro";
  const apk = path.resolve(args["apk"]);
  const packageName = args["package"];
  const component = args["component"];
  const rawDir = path.resolve(args["raw-dir"]);
  const scene = args["scene"];
  const repeatCount = Number.parseInt(args["repeat-count"] || "3", 10);
  const timeoutMs = Number.parseInt(args["timeout-ms"] || "240000", 10);

  if (!fs.existsSync(apk)) {
    throw new Error(`APK does not exist: ${apk}`);
  }
  if (!packageName || !component || !scene) {
    throw new Error("--package, --component, and --scene are required");
  }

  fs.mkdirSync(rawDir, { recursive: true });

  const config = {
    adb: adbPath,
    emulator: emulatorPath,
    avd,
  };
  const deviceId = await ensureDevice(config);

  let wmOverrideApplied = false;
  try {
    adb(adbPath, deviceId, ["shell", "wm", "size", "1920x1080"]);
    wmOverrideApplied = true;
    adb(adbPath, deviceId, ["install", "-r", apk]);

    for (let repeatIndex = 1; repeatIndex <= repeatCount; repeatIndex += 1) {
      adb(adbPath, deviceId, ["logcat", "-c"]);
      adb(adbPath, deviceId, ["shell", "am", "force-stop", packageName]);
      adb(adbPath, deviceId, [
        "shell",
        "am",
        "start",
        "-n",
        component,
        "--ez",
        "io.materia.examples.benchmark.MODE",
        "true",
        "--ei",
        "io.materia.examples.benchmark.REPEAT_INDEX",
        String(repeatIndex),
        "--es",
        "io.materia.examples.benchmark.AVD_NAME",
        avd,
      ]);

      const payload = await waitForBenchmarkResult(adbPath, deviceId, scene, timeoutMs);
      payload.environment = payload.environment || {};
      payload.environment.emulator_name = payload.environment.emulator_name || avd;
      payload.environment.notes = Array.from(
        new Set([...(payload.environment.notes || []), "Android emulator benchmark", "Host-GPU-assisted"])
      );

      const outputPath = path.join(rawDir, `${scene}-android-repeat${repeatIndex}.json`);
      fs.writeFileSync(outputPath, JSON.stringify(payload, null, 2));
      console.log(`Wrote ${outputPath}`);
    }
  } finally {
    if (wmOverrideApplied) {
      adb(adbPath, deviceId, ["shell", "wm", "size", "reset"]);
    }
    adb(adbPath, deviceId, ["shell", "am", "force-stop", packageName]);
  }
}

main().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exitCode = 1;
});
