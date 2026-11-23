#!/bin/bash
# Renders the repo's social preview (docs/social-preview.png, 1280×640) from docs/social-preview.html.
# The PNG is uploaded by hand on GitHub: Settings → General → Social preview.
#
# Run it whenever the template, the app's colours/fonts or the profile pictures change, then commit
# docs/social-preview.html + docs/social-preview.png and upload the new PNG.
#
# Needs Node (npx). The first run downloads Playwright and its Chromium; the template loads Rajdhani
# and Nunito from Google Fonts, so it needs network access.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEMPLATE="$REPO_ROOT/docs/social-preview.html"
OUTPUT="$REPO_ROOT/docs/social-preview.png"
PLAYWRIGHT="playwright@${PLAYWRIGHT_VERSION:-1}"

echo "🧩 Making sure Playwright's Chromium is installed…"
npx -y "$PLAYWRIGHT" install chromium >/dev/null

echo "🖼  Rendering $TEMPLATE…"
# The wait gives the Google Fonts (display=block) time to arrive before the shot is taken.
npx -y "$PLAYWRIGHT" screenshot \
  --browser=chromium \
  --viewport-size=1280,640 \
  --wait-for-timeout=2000 \
  "file://$TEMPLATE" "$OUTPUT"

echo "✅ Wrote $OUTPUT"
echo "   Upload it on GitHub: Settings → General → Social preview."
