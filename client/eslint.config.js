// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');
const sonarjs = require('eslint-plugin-sonarjs');
const boundaries = require('eslint-plugin-boundaries');

/** A boundaries selector for files in any of these categories (see boundaries/files). */
const file = (...categories) => ({ file: { categories } });

module.exports = defineConfig([
  {
    ignores: ['src/generated/**'],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
      sonarjs.configs.recommended,
    ],
    processor: angular.processInlineTemplates,
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: __dirname,
      },
    },
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-deprecated': 'error',
      '@typescript-eslint/prefer-readonly': 'error',
      '@typescript-eslint/no-floating-promises': 'error',
      'no-console': ['error', { allow: ['warn', 'error'] }],
      'eqeqeq': ['error', 'always', { null: 'ignore' }],
      // Size caps from ChecklistForProjects: one responsibility per file and per function.
      'max-lines': ['error', { max: 200, skipBlankLines: true, skipComments: true }],
      'max-lines-per-function': ['error', { max: 25, skipBlankLines: true, skipComments: true }],
      'max-classes-per-file': ['error', 1],
      'sonarjs/cognitive-complexity': ['error', 15],
      // TODO markers are allowed: they point at known debt, such as a ratchet to lower.
      'sonarjs/todo-tag': 'off',
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/generated/model/!(models)'],
              message: "Use the model barrel: import from '…/generated/model/models'",
            },
            {
              group: ['**/generated/api/!(api)'],
              message: "Use the API barrel: import from '…/generated/api/api'",
            },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.spec.ts'],
    rules: {
      // Specs hold fixtures and long describe/it callbacks.
      'max-lines': 'off',
      'max-lines-per-function': 'off',
    },
  },
  // ── Layer boundaries ─────────────────────────────────────────────────────────
  // A component may use the generated DTO types but never the generated API services: data access
  // goes through an app service, so components don't grow into god objects.
  //
  // Files are grouped by feature, so layers are recognised by file suffix (boundaries/files). A
  // file can have several categories, and the LAST matching policy decides. Angular gives
  // components no suffix (app.ts), so a component is any app file without a layer category.
  //
  // The TypeScript resolver is load-bearing: without it no import resolves to a file, nothing is
  // classified, and the rule passes on any code.
  {
    files: ['src/app/**/*.ts'],
    ignores: ['**/*.spec.ts'],
    plugins: { boundaries },
    settings: {
      'import/resolver': {
        typescript: { project: 'tsconfig.json' },
      },
      'boundaries/files': [
        { pattern: 'src/app/**/*.ts', category: 'app-file' },
        { pattern: 'src/app/app{,.config,.routes}.ts', category: 'bootstrap' },
        { pattern: 'src/app/**/*.service.ts', category: 'service' },
        { pattern: 'src/app/**/*.util.ts', category: 'util' },
        { pattern: 'src/app/**/*.guard.ts', category: 'guard' },
        { pattern: 'src/app/**/*.interceptor.ts', category: 'interceptor' },
        { pattern: 'src/generated/**/*', category: 'generated' },
        { pattern: 'src/generated/api/**/*', category: 'generated-api' },
      ],
    },
    rules: {
      // These also flag external packages and bootstrap files; the dependency rule carries the signal.
      'boundaries/no-unknown': 'off',
      'boundaries/no-unknown-files': 'off',
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          policies: [
            // Anything in the app may use other app files and the generated types…
            { from: file('app-file'), allow: { to: file('app-file', 'generated') } },
            // …but not the generated API services: those go through an app service.
            { from: file('app-file'), disallow: { to: file('generated-api') } },
            // Services and utils sit below the UI: they may not import components or guards.
            { from: file('service', 'util'), disallow: { to: file('app-file') } },
            { from: file('service'), allow: { to: file('service', 'util') } },
            { from: file('util'), allow: { to: file('util') } },
            { from: file('service', 'interceptor'), allow: { to: file('generated-api') } },
            // The bootstrap files wire everything together, the API providers included.
            { from: file('bootstrap'), allow: { to: file('app-file', 'generated') } },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
