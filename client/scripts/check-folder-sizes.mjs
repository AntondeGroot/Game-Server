// Fails when a folder under src/ holds more than MAX non-test files. A folder that big has
// sub-concepts it hasn't named yet: split it by concept, not by kind (no utils/ or helpers/).
// Catalogue folders that are looked things up in rather than read may run long; list them in
// EXEMPT, so every exemption is a reviewed decision.
import { execSync } from 'node:child_process';
import { dirname } from 'node:path';

const MAX = 8;
const EXEMPT = new Set([]);

const files = execSync('git ls-files src', { encoding: 'utf8' })
  .split('\n')
  .filter((file) => file && !/\.(spec|test)\./.test(file));

const counts = new Map();
for (const file of files) {
  const folder = dirname(file);
  counts.set(folder, (counts.get(folder) ?? 0) + 1);
}

const tooBig = [...counts].filter(([folder, count]) => count > MAX && !EXEMPT.has(folder));
for (const [folder, count] of tooBig) {
  console.error(`${folder} has ${count} files (max ${MAX}). Split it by concept.`);
}
process.exit(tooBig.length ? 1 : 0);
