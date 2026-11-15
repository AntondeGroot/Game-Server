// The generated client is not held to this project's strict compiler options, so each
// generated file opts out of type checking.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

function visit(folder) {
  for (const entry of readdirSync(folder, { withFileTypes: true })) {
    const path = join(folder, entry.name);
    if (entry.isDirectory()) {
      visit(path);
    } else if (entry.name.endsWith('.ts')) {
      const source = readFileSync(path, 'utf8');
      if (!source.startsWith('// @ts-nocheck')) writeFileSync(path, '// @ts-nocheck\n' + source);
    }
  }
}

visit('src/generated');
