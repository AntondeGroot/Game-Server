// `npm audit --audit-level=high`, minus the advisories in IGNORED. npm can't skip one advisory, and
// an unfixable one would otherwise keep CI red and hide any new finding behind it. Every entry is a
// reviewed decision with a TODO: remove it as soon as a fixed version exists.
import { spawnSync } from 'node:child_process';

const FAILING_SEVERITIES = new Set(['high', 'critical']);
const IGNORED = new Map([
  [
    // TODO: remove once picomatch ships a fix. ReDoS through repeated extglobs; every version is
    // affected. It only matches globs from our own config and tooling, never user input.
    'GHSA-fc5q-39xc-vjpf',
    'picomatch ReDoS',
  ],
]);

// npm audit exits non-zero whenever it finds something, so read the report, not the exit code.
const { stdout } = spawnSync('npm', ['audit', '--json'], { encoding: 'utf8' });
const report = JSON.parse(stdout);

// A package's `via` lists advisories (objects) and the vulnerable packages it depends on (names).
// Only the objects are findings; the names point back at one of them.
const advisories = new Map();
for (const vulnerability of Object.values(report.vulnerabilities ?? {})) {
  for (const via of vulnerability.via) {
    if (typeof via === 'object' && FAILING_SEVERITIES.has(via.severity)) {
      advisories.set(via.url.split('/').pop(), via);
    }
  }
}

const failing = [...advisories].filter(([id]) => !IGNORED.has(id));
for (const [id, advisory] of failing) {
  console.error(`${advisory.severity}: ${advisory.name} — ${advisory.title} (${id})`);
}
for (const [id, reason] of IGNORED) {
  const status = advisories.has(id) ? 'ignored' : 'no longer reported, remove it from IGNORED';
  console.warn(`${id} (${reason}): ${status}`);
}
process.exit(failing.length ? 1 : 0);
