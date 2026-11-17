#!/bin/bash
set -e

# Usage: ./setup-pi.sh [target]   (default: my-pi, falling back to my-pi-ext)
if [ -n "$1" ]; then
  TARGET="$1"
elif ssh -o ConnectTimeout=3 -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectionAttempts=1 my-pi true 2>/dev/null; then
  TARGET=my-pi
else
  echo "⚠️  my-pi unreachable, falling back to my-pi-ext (Cloudflare Tunnel)..."
  TARGET=my-pi-ext
fi
SSH="ssh -i $HOME/.ssh/pi_deploy_key $TARGET"
SCP="scp -i $HOME/.ssh/pi_deploy_key"

CONFIG_FILE="./deploy.local.conf"

USE_NAMED_TUNNEL=false

if [ -s "$CONFIG_FILE" ]; then
  source "$CONFIG_FILE"

  if [ -n "$DOMAINS" ] && [ -n "$TUNNEL_ID" ] && [ -n "$CREDENTIALS_FILE" ]; then
    USE_NAMED_TUNNEL=true
  else
    echo "⚠️  $CONFIG_FILE exists but is missing DOMAINS, TUNNEL_ID, or CREDENTIALS_FILE."
    echo "   Falling back to random trycloudflare tunnel."
  fi
else
  echo "ℹ️  No non-empty $CONFIG_FILE found. Using random trycloudflare tunnel."
fi

# Through the tunnel, this script's own SSH connection depends on the tunnel config it rewrites.
# A quick tunnel or a config without SSH_DOMAIN has no SSH route, so the Pi would become
# unreachable from outside: refuse rather than lock ourselves out.
if [ "$TARGET" = my-pi-ext ] && { [ "$USE_NAMED_TUNNEL" != true ] || [ -z "$SSH_DOMAIN" ]; }; then
  echo "❌ Through my-pi-ext this needs a named tunnel with SSH_DOMAIN in $CONFIG_FILE;"
  echo "   otherwise the new tunnel config has no SSH route and locks you out. Run it at home."
  exit 1
fi

echo "📁 Creating directory..."
$SSH "sudo mkdir -p /opt/gameroom"

echo "🧹 Capping journald disk usage..."
# The journal is shared by every service on the Pi; without a cap it grew to ~2.8 GB.
$SSH "sudo mkdir -p /etc/systemd/journald.conf.d && \
      printf '[Journal]\nSystemMaxUse=300M\n' | sudo tee /etc/systemd/journald.conf.d/size.conf > /dev/null && \
      sudo systemctl restart systemd-journald"

echo "📦 Installing nginx..."
$SSH "sudo apt-get install -y nginx"

echo "⚙️  Configuring nginx..."
$SCP nginx.conf "$TARGET:/home/ubuntu/gameroom-nginx.conf"
$SSH "sudo mv /home/ubuntu/gameroom-nginx.conf /etc/nginx/sites-available/gameroom && \
      sudo ln -sf /etc/nginx/sites-available/gameroom /etc/nginx/sites-enabled/gameroom && \
      sudo rm -f /etc/nginx/sites-enabled/default && \
      sudo fuser -k 80/tcp || true; sudo nginx -t && sudo systemctl enable nginx && sudo systemctl restart nginx"

echo "⚙️  Installing gameroom systemd service..."
$SSH "sudo tee /etc/systemd/system/gameroom.service > /dev/null << 'EOF'
[Unit]
Description=GameRoom
After=network.target

[Service]
User=ubuntu
# Grant the service read access to journald (systemd-journal) and nginx logs (adm)
# so the admin "Ops · Error Logs" page can read them without sudo.
SupplementaryGroups=systemd-journal adm
ExecStart=/usr/bin/java -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError --add-opens java.base/java.lang=ALL-UNNAMED -jar /opt/gameroom/gameroom.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF"
# Also add the ubuntu user to these groups (harmless if the unit already grants them).
$SSH "sudo usermod -aG systemd-journal,adm ubuntu || true"
$SSH "sudo systemctl daemon-reload && sudo systemctl enable gameroom"

"$(dirname "$0")/setup-tomcat.sh" "$TARGET"

echo "☁️  Installing cloudflared..."
$SSH "if ! command -v cloudflared &>/dev/null; then
  curl -fsSL https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64 -o /tmp/cloudflared
  sudo mv /tmp/cloudflared /usr/local/bin/cloudflared
  sudo chmod +x /usr/local/bin/cloudflared
fi"

if [ "$USE_NAMED_TUNNEL" = true ]; then
  echo "⚙️  Installing cloudflared named tunnel config for $DOMAINS..."

  TMP_CLOUDFLARED_CONFIG="$(mktemp)"

  INGRESS_BLOCK=""
  for d in $DOMAINS; do
    INGRESS_BLOCK+="  - hostname: $d
    service: http://localhost:80
"
    # An apex domain (one dot, e.g. example.uk) also answers on its www. subdomain.
    if [[ "$d" != www.* && "$d" =~ ^[^.]+\.[^.]+$ ]]; then
      INGRESS_BLOCK+="  - hostname: www.$d
    service: http://localhost:80
"
    fi
  done
  if [ -n "$SSH_DOMAIN" ]; then
    INGRESS_BLOCK+="  - hostname: $SSH_DOMAIN
    service: ssh://localhost:22
"
  fi
  INGRESS_BLOCK+="  - service: http_status:404"

  cat > "$TMP_CLOUDFLARED_CONFIG" <<EOF
tunnel: $TUNNEL_ID
credentials-file: $CREDENTIALS_FILE

ingress:
$INGRESS_BLOCK
EOF

  $SSH "sudo mkdir -p /etc/cloudflared"
  $SCP "$TMP_CLOUDFLARED_CONFIG" "$TARGET:/home/ubuntu/cloudflared-config.yml"
  rm -f "$TMP_CLOUDFLARED_CONFIG"

  $SSH "sudo mv /home/ubuntu/cloudflared-config.yml /etc/cloudflared/config.yml"

  echo "⚙️  Installing cloudflared named-tunnel systemd service..."
  $SSH "sudo tee /etc/systemd/system/cloudflared.service > /dev/null << 'EOF'
[Unit]
Description=Cloudflare Named Tunnel
After=network.target

[Service]
ExecStart=/usr/local/bin/cloudflared tunnel run
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF"

else
  echo "⚙️  Installing cloudflared quick-tunnel systemd service..."

  $SSH "sudo rm -f /etc/cloudflared/config.yml || true"

  $SSH "sudo tee /etc/systemd/system/cloudflared.service > /dev/null << 'EOF'
[Unit]
Description=Cloudflare Quick Tunnel
After=network.target

[Service]
ExecStart=/usr/local/bin/cloudflared tunnel --url http://localhost:80
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF"
fi

$SSH "sudo systemctl daemon-reload && sudo systemctl enable cloudflared"

echo "📝 Writing games config..."
$SCP GameRoom-server/src/main/resources/games.yaml "$TARGET:/home/ubuntu/games.yaml"
$SSH "sudo mv /home/ubuntu/games.yaml /opt/gameroom/games.yaml"

echo "🔄 Restarting cloudflared..."
# Last, and detached through systemd-run: through my-pi-ext the restart cuts this SSH connection,
# and the restart must still happen. Then reconnect (through the restarted tunnel, if that is the
# route) to check it came back.
$SSH "sudo systemd-run --on-active=2 --unit=setup-restart-cloudflared-\$(date +%s) --collect systemctl restart cloudflared"
echo "⏳ Waiting for cloudflared to come back..."
sleep 5
cloudflared_up=false
for _ in $(seq 1 12); do
  if ssh -i "$HOME/.ssh/pi_deploy_key" -o ConnectTimeout=10 -o BatchMode=yes "$TARGET" \
      "systemctl is-active --quiet cloudflared" 2>/dev/null; then
    cloudflared_up=true
    break
  fi
  sleep 5
done
if [ "$cloudflared_up" != true ]; then
  echo "❌ cloudflared did not come back within a minute."
  echo "   At home: ssh my-pi 'systemctl status cloudflared; journalctl -u cloudflared -n 50'"
  exit 1
fi

echo ""
echo "✅ Pi setup complete."
echo "   Run ./deploy.sh to deploy the application."

if [ "$USE_NAMED_TUNNEL" = true ]; then
  for d in $DOMAINS; do
    echo "   Public URL: https://$d"
    if [[ "$d" != www.* && "$d" =~ ^[^.]+\.[^.]+$ ]]; then
      echo "   Public URL: https://www.$d"
    fi
  done
  echo ""
  echo "🌐 Each hostname needs a CNAME DNS record in Cloudflare (proxied):"
  for d in $DOMAINS; do
    if [[ "$d" != www.* && "$d" =~ ^[^.]+\.[^.]+$ ]]; then
      # Cloudflare allows a CNAME at the apex ("@") through CNAME flattening.
      echo "   Add a CNAME DNS record in Cloudflare: @ ($d) → $TUNNEL_ID.cfargotunnel.com"
      echo "   Add a CNAME DNS record in Cloudflare: www.$d → $TUNNEL_ID.cfargotunnel.com"
    else
      echo "   Add a CNAME DNS record in Cloudflare: $d → $TUNNEL_ID.cfargotunnel.com"
    fi
  done
else
  echo "   To find your public tunnel URL, run: ./get-tunnel-url.sh"
fi

if [ -n "$SSH_DOMAIN" ]; then
  echo ""
  echo "🔑 SSH over Cloudflare Tunnel is configured for: $SSH_DOMAIN"
  echo "   Add a CNAME DNS record in Cloudflare: $SSH_DOMAIN → $TUNNEL_ID.cfargotunnel.com"
  echo ""
  echo "   On your Mac, install cloudflared if you haven't already:"
  echo "     brew install cloudflared"
  echo ""
  echo "   Add this to ~/.ssh/config on your Mac:"
  echo "     Host my-pi-ext"
  echo "       HostName $SSH_DOMAIN"
  echo "       User ubuntu"
  echo "       IdentityFile ~/.ssh/pi_deploy_key"
  echo "       ProxyCommand cloudflared access ssh --hostname %h"
  echo ""
  echo "   Then deploy from any network with: ./deploy.sh my-pi-ext"
fi