#!/bin/bash
# Runs on the Pi as root, started by update-server.sh through systemd-run. It runs detached
# from the SSH session on purpose: upgrading packages or cloudflared restarts services, and
# when the session comes in through the Cloudflare tunnel, restarting cloudflared cuts it.
#
# Usage: update-server-on-pi.sh <run-id> <reboot-if-needed: yes|no>
# The result is written to $STATUS_FILE as "<run-id> <exit-code>" for update-server.sh to poll.
set -uo pipefail

RUN_ID="$1"
REBOOT_IF_NEEDED="$2"
STATUS_FILE=/var/lib/update-server/status
CLOUDFLARED=/usr/local/bin/cloudflared
SERVICES="nginx gameroom tomcat10 cloudflared"
failures=0

step() { echo; echo "=== $* ==="; }

upgrade_packages() {
  step "Upgrading apt packages"
  export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a
  # confdef/confold keep locally edited config files (nginx, systemd units) instead of
  # stopping to ask, which a detached run could never answer.
  local keep_config=(-o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold)
  if ! { apt-get update &&
         apt-get -y "${keep_config[@]}" upgrade --with-new-pkgs &&
         apt-get -y autoremove; }; then
    echo "!! apt failed"; failures=$((failures + 1))
  fi
}

# cloudflared was installed as a plain binary (setup-pi.sh), so apt never updates it.
update_cloudflared() {
  step "Updating cloudflared"
  local installed latest arch
  installed=$("$CLOUDFLARED" --version | awk '{print $3}')
  latest=$(curl -fsSLI -o /dev/null -w '%{url_effective}' \
    https://github.com/cloudflare/cloudflared/releases/latest | sed 's#.*/tag/##')
  if [ -z "$latest" ]; then
    echo "!! Could not read the latest cloudflared release"; failures=$((failures + 1)); return
  fi
  if [ "$installed" = "$latest" ]; then
    echo "cloudflared $installed is the latest release"; return
  fi
  case "$(uname -m)" in
    aarch64) arch=arm64 ;;
    x86_64) arch=amd64 ;;
    *) echo "!! Unsupported architecture $(uname -m)"; failures=$((failures + 1)); return ;;
  esac
  echo "Updating cloudflared $installed -> $latest"
  install_cloudflared "$latest" "$arch"
}

install_cloudflared() {
  local version="$1" arch="$2" download
  download=$(mktemp)
  if ! curl -fsSL -o "$download" \
      "https://github.com/cloudflare/cloudflared/releases/download/$version/cloudflared-linux-$arch" ||
     ! chmod +x "$download" ||
     ! "$download" --version | grep -q "$version"; then
    echo "!! Download of cloudflared $version failed or does not run; keeping the current one"
    rm -f "$download"; failures=$((failures + 1)); return
  fi
  cp "$CLOUDFLARED" "$CLOUDFLARED.previous"
  mv "$download" "$CLOUDFLARED"
  systemctl restart cloudflared
  sleep 15
  if ! systemctl is-active --quiet cloudflared; then
    echo "!! cloudflared $version did not stay up; rolling back"
    mv "$CLOUDFLARED.previous" "$CLOUDFLARED"
    systemctl restart cloudflared
    failures=$((failures + 1))
  fi
}

check_services() {
  step "Checking services"
  local service
  for service in $SERVICES; do
    if systemctl is-active --quiet "$service"; then
      echo "ok    $service"
    else
      echo "DOWN  $service"; failures=$((failures + 1))
    fi
  done
}

finish() {
  mkdir -p "$(dirname "$STATUS_FILE")"
  echo "$RUN_ID $failures" > "$STATUS_FILE"
  if [ -f /var/run/reboot-required ]; then
    step "A reboot is required"
    cat /var/run/reboot-required.pkgs 2>/dev/null
    if [ "$REBOOT_IF_NEEDED" = yes ]; then
      echo "Rebooting now"
      echo "$RUN_ID $failures rebooted" > "$STATUS_FILE"
      systemctl reboot
    fi
  fi
}

upgrade_packages
update_cloudflared
check_services
finish
