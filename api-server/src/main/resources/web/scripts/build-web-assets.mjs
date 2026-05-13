import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import CleanCSS from "clean-css";
import { minify } from "html-minifier-terser";
import JavaScriptObfuscator from "javascript-obfuscator";

function readArg(name, defaultValue = "") {
  const key = `--${name}`;
  const idx = process.argv.indexOf(key);
  if (idx >= 0 && idx + 1 < process.argv.length) {
    return process.argv[idx + 1];
  }
  return defaultValue;
}

const srcArg = readArg("src", "./");
const outArg = readArg("out", "./dist");

const srcRoot = path.resolve(process.cwd(), srcArg);
const outRoot = path.resolve(process.cwd(), outArg);

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function walk(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    if (entry.name === "dist" || entry.name === "node_modules") {
      continue;
    }
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await walk(full)));
    } else if (entry.isFile()) {
      files.push(full);
    }
  }
  return files;
}

function obfuscateJs(content) {
  return JavaScriptObfuscator.obfuscate(content, {
    compact: true,
    simplify: true,
    stringArray: true,
    stringArrayEncoding: ["base64"],
    stringArrayThreshold: 0.8,
    splitStrings: true,
    splitStringsChunkLength: 8,
    transformObjectKeys: true,
    renameGlobals: false,
    deadCodeInjection: false,
    selfDefending: true
  }).getObfuscatedCode();
}

function minifyCss(content, file) {
  const output = new CleanCSS({ level: 2 }).minify(content);
  if (output.errors && output.errors.length) {
    throw new Error(`CSS minify failed for ${file}: ${output.errors.join("; ")}`);
  }
  return output.styles;
}

async function minifyHtml(content) {
  return minify(content, {
    collapseWhitespace: true,
    removeComments: true,
    removeRedundantAttributes: true,
    removeScriptTypeAttributes: true,
    removeStyleLinkTypeAttributes: true,
    useShortDoctype: true,
    minifyCSS: true,
    minifyJS: true
  });
}

async function processFile(absFile) {
  const relative = path.relative(srcRoot, absFile);
  const outFile = path.join(outRoot, relative);
  await ensureDir(path.dirname(outFile));
  const ext = path.extname(absFile).toLowerCase();

  if (ext === ".js") {
    const content = await fs.readFile(absFile, "utf8");
    const transformed = obfuscateJs(content);
    await fs.writeFile(outFile, transformed, "utf8");
    return;
  }

  if (ext === ".css") {
    const content = await fs.readFile(absFile, "utf8");
    const transformed = minifyCss(content, relative);
    await fs.writeFile(outFile, transformed, "utf8");
    return;
  }

  if (ext === ".html") {
    const content = await fs.readFile(absFile, "utf8");
    const transformed = await minifyHtml(content);
    await fs.writeFile(outFile, transformed, "utf8");
    return;
  }

  await fs.copyFile(absFile, outFile);
}

async function run() {
  await fs.rm(outRoot, { recursive: true, force: true });
  await ensureDir(outRoot);
  const files = await walk(srcRoot);
  await Promise.all(files.map(processFile));
  process.stdout.write(`Built ${files.length} web assets into ${outRoot}\n`);
}

run().catch((err) => {
  process.stderr.write(`Web build failed: ${err.message}\n`);
  process.exit(1);
});
