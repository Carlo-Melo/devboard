import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const productionRoots = [
  path.join(root, 'devBoard-backend/src/main/java'),
  path.join(root, 'devBoard-frontend/src')
];
const sourceExtensions = new Set(['.java', '.ts', '.html', '.scss']);

function walk(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const target = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(target) : [target];
  });
}

function relative(file) {
  return path.relative(root, file).replaceAll(path.sep, '/');
}

function readLines(file) {
  return fs.readFileSync(file, 'utf8').replaceAll('\r\n', '\n').split('\n');
}

const files = productionRoots
  .flatMap(walk)
  .filter(file => sourceExtensions.has(path.extname(file)))
  .filter(file => !file.endsWith('.spec.ts'));

const fileStats = files.map(file => {
  const lines = readLines(file);
  const commentLines = lines.filter(line => /^\s*(\/\/|\/\*|\*|<!--)/.test(line)).length;
  const todoLocations = lines
    .map((line, index) => ({ line, number: index + 1 }))
    .filter(item => /^\s*(\/\/|\/\*|\*|<!--)/.test(item.line))
    .filter(item => /\b(TODO|FIXME|HACK|XXX)\b/.test(item.line))
    .map(item => item.number);
  return { file: relative(file), lines: lines.length, commentLines, todoMarkers: todoLocations.length, todoLocations };
});

const javaNamingViolations = files
  .filter(file => file.endsWith('.java'))
  .filter(file => !/^[A-Z][A-Za-z0-9]*$/.test(path.basename(file, '.java')))
  .map(relative);

const frontendNamingViolations = files
  .filter(file => file.includes(`${path.sep}devBoard-frontend${path.sep}`))
  .filter(file => {
    const name = path.basename(file);
    if (name.startsWith('_') && name.endsWith('.scss')) return false;
    return !/^[a-z0-9]+(?:-[a-z0-9]+)*(?:\.[a-z0-9-]+)*\.(ts|html|scss)$/.test(name);
  })
  .map(relative);

function normalizeLine(line) {
  return line
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/\/\/.*$/, '')
    .trim();
}

const duplicateWindowSize = 7;
const windows = new Map();
for (const file of files) {
  const lines = readLines(file);
  for (let index = 0; index <= lines.length - duplicateWindowSize; index += 1) {
    const normalized = lines.slice(index, index + duplicateWindowSize).map(normalizeLine);
    const meaningful = normalized.filter(line => line && !/^[{}()[\],;]+$/.test(line));
    if (meaningful.length < 5) continue;
    if (meaningful.some(line => /^(package|import)\s/.test(line))) continue;
    const signature = normalized.join('\n');
    if (signature.length < 160) continue;
    const occurrence = { file: relative(file), line: index + 1 };
    const current = windows.get(signature) ?? [];
    current.push(occurrence);
    windows.set(signature, current);
  }
}

const duplicateCandidates = [...windows.entries()]
  .map(([signature, occurrences]) => ({
    signature,
    occurrences: occurrences.filter((item, index, all) => all.findIndex(other => other.file === item.file && other.line === item.line) === index)
  }))
  .filter(group => new Set(group.occurrences.map(item => item.file)).size > 1)
  .sort((a, b) => b.signature.length - a.signature.length);

const duplicateBlocks = [];
for (const candidate of duplicateCandidates) {
  const filesKey = [...new Set(candidate.occurrences.map(item => item.file))].sort().join('|');
  const overlaps = duplicateBlocks.some(existing => {
    const existingKey = [...new Set(existing.occurrences.map(item => item.file))].sort().join('|');
    if (filesKey !== existingKey) return false;
    return candidate.occurrences.some(item => existing.occurrences.some(other => item.file === other.file && Math.abs(item.line - other.line) < duplicateWindowSize));
  });
  if (!overlaps) {
    duplicateBlocks.push({
      lines: duplicateWindowSize,
      occurrences: candidate.occurrences,
      preview: candidate.signature.split('\n').filter(Boolean).slice(0, 3)
    });
  }
  if (duplicateBlocks.length === 12) break;
}

const frontendText = files
  .filter(file => file.includes(`${path.sep}devBoard-frontend${path.sep}`))
  .map(file => fs.readFileSync(file, 'utf8'))
  .join('\n');
const exportedSymbols = [];
for (const file of files.filter(file => file.endsWith('.ts'))) {
  const text = fs.readFileSync(file, 'utf8');
  const matcher = /export\s+(?:default\s+)?(?:abstract\s+)?(?:class|interface|type|const|function|enum)\s+([A-Za-z_$][\w$]*)/g;
  for (const match of text.matchAll(matcher)) {
    const name = match[1];
    const references = frontendText.match(new RegExp(`\\b${name}\\b`, 'g'))?.length ?? 0;
    exportedSymbols.push({ name, file: relative(file), references });
  }
}

function gitLines(...args) {
  return execFileSync('git', args, { cwd: root, encoding: 'utf8' })
    .trim()
    .split(/\r?\n/)
    .filter(Boolean);
}

const commitSubjects = gitLines('log', '--all', '--format=%s');
const authors = gitLines('log', '--all', '--format=%an');
const authorEmails = gitLines('log', '--all', '--format=%ae');
const conventionalPattern = /^(feat|fix|docs|chore|style|refactor|test|build|ci|perf)(\([^)]+\))?: /;
const genericPattern = /^(ajuste|ajustes|teste|test|fix|update|mudan[cç]as|altera[cç][oõ]es?)$/i;
const frontendPackage = JSON.parse(fs.readFileSync(path.join(root, 'devBoard-frontend/package.json'), 'utf8'));
const backendPom = fs.readFileSync(path.join(root, 'devBoard-backend/pom.xml'), 'utf8');

const report = {
  generatedAt: new Date().toISOString(),
  scope: {
    productionFiles: files.length,
    sourceLines: fileStats.reduce((total, item) => total + item.lines, 0),
    commentLines: fileStats.reduce((total, item) => total + item.commentLines, 0),
    todoMarkers: fileStats.reduce((total, item) => total + item.todoMarkers, 0)
  },
  naming: {
    javaViolations: javaNamingViolations,
    frontendViolations: frontendNamingViolations
  },
  maintainability: {
    todoMarkers: fileStats
      .filter(item => item.todoMarkers > 0)
      .map(item => ({ file: item.file, lines: item.todoLocations })),
    filesOver300Lines: fileStats.filter(item => item.lines > 300).sort((a, b) => b.lines - a.lines),
    filesOver200Lines: fileStats.filter(item => item.lines > 200).sort((a, b) => b.lines - a.lines),
    duplicateBlocks,
    unreferencedExportCandidates: exportedSymbols.filter(item => item.references === 1)
  },
  git: {
    auditedCommit: execFileSync('git', ['rev-parse', '--short', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim(),
    commits: commitSubjects.length,
    conventionalCommits: commitSubjects.filter(subject => conventionalPattern.test(subject)).length,
    genericCommits: commitSubjects.filter(subject => genericPattern.test(subject)),
    mergeCommits: Number(execFileSync('git', ['rev-list', '--all', '--merges', '--count'], { cwd: root, encoding: 'utf8' }).trim()),
    authorNames: [...new Set(authors)],
    authorEmails: [...new Set(authorEmails)],
    branches: gitLines('branch', '--all', '--format=%(refname:short)')
  },
  automation: {
    rootReadme: fs.existsSync(path.join(root, 'README.md')),
    frontendReadme: fs.existsSync(path.join(root, 'devBoard-frontend/README.md')),
    editorConfig: fs.existsSync(path.join(root, 'devBoard-frontend/.editorconfig')),
    frontendLintScript: Object.hasOwn(frontendPackage.scripts ?? {}, 'lint'),
    mavenWrapper: fs.existsSync(path.join(root, 'devBoard-backend/mvnw')) || fs.existsSync(path.join(root, 'devBoard-backend/mvnw.cmd')),
    backendFormatterOrLinter: /checkstyle|spotless|pmd/i.test(backendPom),
    ciWorkflows: walk(path.join(root, '.github/workflows')).filter(file => /\.(yml|yaml)$/.test(file)).map(relative)
  }
};

export { report };

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  console.log(JSON.stringify(report, null, 2));
}
