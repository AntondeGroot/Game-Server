#!/bin/bash
# Updates the Pi: apt packages, and cloudflared (a plain binary apt does not manage).
# The work itself runs on the Pi (ops/update-server-on-pi.sh), detached from SSH, so a restart of
# cloudflared mid-update cannot kill it when we are connected through the tunnel.
#
# Usage: ./update-server.sh [--reboot] [--force] [target]
#   --reboot  reboot afterwards if the upgrade asks for it (otherwise only reported)
#   --force   update even while rooms are open (upgrading Java or Tomcat restarts the
#             game room and the games, which loses every room and running game)
set -euo pipefail

REBOOT=no
FORCE=no
TARGET=""
for arg in "$@"; do
  case "$arg" in
    --reboot) REBOOT=yes ;;
    --force) FORCE=yes ;;
    -*) echo "Unknown option $arg"; exit 2 ;;
    *) TARGET="$arg" ;;
  esac
done

if [ -z "$TARGET" ]; then
  if ssh -o ConnectTimeout=3 -o BatchMode=yes -o ConnectionAttempts=1 my-pi true 2>/dev/null; then
    TARGET=my-pi
  else
    echo "⚠️  my-pi unreachable, falling back to my-pi-ext (Cloudflare Tunnel)..."
    TARGET=my-pi-ext
  fi
fi
SSH_OPTS=(-i ~/.ssh/pi_deploy_key -o BatchMode=yes -o ConnectTimeout=15)
# Arguments are meant to expand here, on the Mac, before they are sent.
# shellcheck disable=SC2029
remote() { ssh "${SSH_OPTS[@]}" "$TARGET" "$@"; }

WAIT_LIMIT_SECONDS=1800
POLL_SECONDS=10
RUN_ID=$(date +%Y%m%d-%H%M%S)
UNIT="update-server-$RUN_ID"

open_rooms() {
  # The first lobby frame lists every room; the seeded "Test Room" does not count.
  remote "curl -s -N --max-time 2 http://localhost:4100/lobby/stream | head -n 1" |
    grep -o '"name":"[^"]*"' | grep -v '"Test Room"' | sed 's/"name":"\(.*\)"/\1/' || true
}

refuse_while_rooms_open() {
  local rooms
  rooms=$(open_rooms)
  if [ -n "$rooms" ] && [ "$FORCE" != yes ]; then
    echo "🛑 Rooms are open, and an upgrade may restart the game room and lose them:"
    echo "$rooms" | while IFS= read -r room; do echo "     $room"; done
    echo "   Run again when they are empty, or pass --force."
    exit 1
  fi
}

start_update() {
  echo "🚀 Starting update $RUN_ID on $TARGET..."
  scp "${SSH_OPTS[@]}" ops/update-server-on-pi.sh "$TARGET:/tmp/update-server-on-pi.sh"
  remote "sudo systemd-run --unit=$UNIT --collect bash /tmp/update-server-on-pi.sh $RUN_ID $REBOOT"
}

# Prints the status line once this run has written it. SSH failures are expected while
# cloudflared restarts or the Pi reboots, so they just mean "ask again".
wait_for_status() {
  local waited=0 status
  echo "⏳ Waiting for the update to finish (up to $((WAIT_LIMIT_SECONDS / 60)) minutes)..." >&2
  while [ "$waited" -lt "$WAIT_LIMIT_SECONDS" ]; do
    sleep "$POLL_SECONDS"
    waited=$((waited + POLL_SECONDS))
    status=$(remote "cat /var/lib/update-server/status 2>/dev/null" 2>/dev/null || true)
    case "$status" in
      "$RUN_ID "*) echo "$status"; return 0 ;;
    esac
  done
  echo "❌ No result after $((WAIT_LIMIT_SECONDS / 60)) minutes. Check: journalctl -u $UNIT" >&2
  return 1
}

wait_until_reachable() {
  echo "🔁 Waiting for the Pi to come back after the reboot..."
  sleep 30
  until remote "systemctl is-system-running --wait >/dev/null 2>&1; true" 2>/dev/null; do
    sleep "$POLL_SECONDS"
  done
}

public_site_status() {
  local domain=""
  if [ -f deploy.local.conf ]; then
    # shellcheck disable=SC1091
    domain=$(. ./deploy.local.conf; echo "${DOMAINS%% *}")
  fi
  [ -n "$domain" ] || return 0
  echo "🌐 https://$domain/ answers $(curl -s -o /dev/null -w '%{http_code}' "https://$domain/")"
}

refuse_while_rooms_open
start_update
status=$(wait_for_status)
failures=$(echo "$status" | awk '{print $2}')

if [[ "$status" == *rebooted ]]; then
  wait_until_reachable
  remote "for s in nginx gameroom tomcat10 cloudflared; do printf '%-12s %s\n' \$s \$(systemctl is-active \$s); done"
fi

echo
echo "📜 Log:"
remote "journalctl -u $UNIT -o cat --no-pager"
if remote "test -f /var/run/reboot-required" 2>/dev/null; then
  echo "ℹ️  A reboot is still required. Run again with --reboot, or: ssh $TARGET sudo reboot"
fi
public_site_status

if [ "$failures" != 0 ]; then
  echo "❌ Finished with $failures problem(s); see the log above."
  exit 1
fi
echo "✅ Server updated."
