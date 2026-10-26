# Raspberry Pi Game Room

This project was written in GWT in order to get a better understanding of it, since this is used by my employer.
<p align="center">
   <img width="500" alt="image" src="https://github.com/user-attachments/assets/7035146e-9a82-42ae-85d1-6f7a33bca0a2" />
</p>

<p align="center">
   <img width="500" alt="image" src="https://github.com/user-attachments/assets/54ef3dde-b30c-47b6-9569-fbcff16861f3" />
</p>

# Games

## 1. Tock / Keezenspel

<p align="center">
   <a href="https://github.com/AntondeGroot/GWT_Keezenspel"><strong>View Repository</strong></a><br><br>
   <img width="500" alt="image" src="https://github.com/user-attachments/assets/70e1ac8a-416e-4b8a-9593-5207419b20b4" />
</p>

## 2. Qwixx

<p align="center">
   <a href="https://github.com/AntondeGroot/Qwixx"><strong>View Repository</strong></a><br><br>
   <img width="500" alt="image" src="https://github.com/user-attachments/assets/c0e45ab8-958e-4570-a303-cb2ba45c19c4" />
</p>

## Local development

1. Start the backend: `mvn spring-boot:run -pl *-server -am`
2. Start the GWT code server: `mvn gwt:codeserver -pl *-client -am`
3. Open http://localhost:4100/

---
# Deploying to Raspberry Pi
<details>
<summary>Deploying to Raspberry Pi</summary>

---

Run `./deploy.sh` to build, upload, and restart the service.

`deploy.sh` auto-detects the target: it first probes `my-pi` (local network); if unreachable it falls back to `my-pi-ext` (Cloudflare Tunnel). You can also force a target explicitly:

```bash
./deploy.sh           # auto-detect
./deploy.sh my-pi     # force local
./deploy.sh my-pi-ext # force tunnel
```

### First-time Pi setup

Run the setup script:

```bash
./setup-pi.sh
```

This will:
- Install and configure nginx
- Install the shared Tomcat 10.1 on `127.0.0.1:8090` (`setup-tomcat.sh`, also runnable on its own). Games that ship as a WAR, such as Qwixx, deploy `<name>.war` into `/var/lib/tomcat10/webapps` and are served at `/<name>`
- Install cloudflared and start it as a service
- Write `/opt/gameroom/games.yaml` with the correct `baseUrl`

The script reads `deploy.local.conf` from the project root to decide which tunnel mode to use:

**Without `deploy.local.conf`** — a temporary public URL is generated, like `https://projector-improving-expired-discussed.trycloudflare.com/`. It changes on every restart but works immediately for quick sessions with friends.

**With `deploy.local.conf`** — a named Cloudflare tunnel is used with a stable custom domain. Set it up first:

1. On the Pi, authenticate and create a named tunnel:
   ```bash
   cloudflared tunnel login
   cloudflared tunnel create gameroom
   cloudflared tunnel route dns gameroom gameroom.yourdomain.com
   ```
   `cloudflared tunnel login` prints a URL — open it on your PC to authenticate. The Pi terminal will then show the tunnel UUID.

2. Create `deploy.local.conf` in the project root (it is gitignored):
   ```
   DOMAINS="gameroom.yourdomain.com"
   TUNNEL_ID=your-tunnel-uuid
   CREDENTIALS_FILE=/home/ubuntu/.cloudflared/your-tunnel-uuid.json

   # Optional: expose SSH over the same tunnel so you can deploy from any network
   SSH_DOMAIN=ssh.yourdomain.com
   ```

3. Run `./setup-pi.sh`

### Deploying from outside your local network (SSH over Cloudflare Tunnel)

If `SSH_DOMAIN` is set in `deploy.local.conf`, `setup-pi.sh` adds an SSH ingress rule to the tunnel. To use it from any network:

1. Add a CNAME DNS record in Cloudflare: `ssh.yourdomain.com` → `<your-tunnel-id>.cfargotunnel.com`

2. Install cloudflared on your Mac (if not already):
   ```bash
   brew install cloudflared
   ```

3. Add a second host alias in `~/.ssh/config`:
   ```
   Host my-pi-ext
       HostName ssh.yourdomain.com
       User ubuntu
       IdentityFile ~/.ssh/pi_deploy_key
       ProxyCommand cloudflared access ssh --hostname %h
   ```

After this, `deploy.sh` will automatically fall back to `my-pi-ext` when `my-pi` is unreachable.

**Keezen and Qwixx** run as `keezen.war` and `qwixx.war` in the shared Tomcat (`setup-tomcat.sh`), which serves each at the context path matching its WAR name. Each game's own `deploy.sh` drops its WAR into `/var/lib/tomcat10/webapps`.
</details>

---
# Creating a new game that integrates with the Game Room
<details>
<summary>Creating a new game that integrates with the Game Room</summary>

---
   
This section documents the contract a game server must fulfil to work with the GameRoom lobby. The GameRoom calls your game server over HTTP; your game does not call the GameRoom directly (except to fetch profile pictures).

## Port & registration

Pick a port that does not clash with existing games and add an entry to `GameRoom-server/src/main/resources/games.yaml` (or `/opt/gameroom/games.yaml` on the Pi):

```yaml
games:
  available:
    - id: mygame
      name: My Game
      baseUrl: http://localhost:4400        # used for server-to-server API calls
      healthUrl: http://localhost:80/mygame/ # used to check if the game is reachable
      minPlayers: 2
      maxPlayers: 4
```

The GameRoom uses `baseUrl` as the root for all API calls to your server. `healthUrl` is polled with a HEAD request to decide whether to show the game in the lobby — typically the nginx path on the Pi. On the Pi, `baseUrl` should use the game's local port (nginx / Cloudflare handles external exposure).

---

## Required REST endpoints

The GameRoom calls the following endpoints on your game server. All request and response bodies are JSON.

### 1. `POST /games` — create a session

Called when the room host starts the game.

**Request body:**
```json
{
  "roomName": "Living Room",
  "maxPlayers": 4,
  "gameOptions": {
    "difficulty": "hard"
  }
}
```

`maxPlayers` is the number of players who actually joined the room (not the configured room maximum). `gameOptions` contains whatever key/value pairs the room host configured in the lobby. Values are typed: `"true"`/`"false"` become booleans, numeric strings become integers, everything else stays a string. It is omitted when no options were set.

**Response body (200):**
```json
{
  "sessionId": "some-unique-id"
}
```

Return a stable ID that identifies this game session for all subsequent calls.

---

### 2. `POST /games/{sessionId}/players` — add a player

Called once per player, in sequence, after the session is created.

**Request body:**
```json
{
  "id": "player-uuid",
  "name": "Alice",
  "profilePic": 3
}
```

`profilePic` is a zero-based global sprite index (see [Profile pictures](#profile-pictures) below). Return any `2xx` status to confirm.

---

### 3. `POST /games/{sessionId}` — start the game

Called after all players have been added. No request body. Return any `2xx` to confirm.

After a successful response the GameRoom redirects each player's browser to:

```
/<gameId>/?sessionid={sessionId}&playerid={playerId}&locale={en|nl|...}
```

`<gameId>` is the `id` field from `games.yaml` (e.g. `/keezen/`). The redirect is a relative path served through nginx — `baseUrl` is used for server-to-server calls only, not for the browser redirect. Use these query parameters to identify who is playing and in which session.

**Error handling:** if steps 2 or 3 fail, the GameRoom immediately sends `DELETE /games/{sessionId}` to clean up. Your server should handle this even for partially-created sessions.

---

### 4. `GET /games/{sessionId}` — session health check

Called every **30 seconds** by the GameRoom to verify the session is still alive.

- Return `2xx` → session is healthy.
- Return any error / connection failure → the GameRoom assumes the session is gone and removes the room from the lobby.

---

### 5. `GET /gamestates/{sessionId}` — game state (for stale detection)

Also polled every **30 seconds**. The GameRoom uses this to detect abandoned games.

**Response body must include a `version` field** that changes whenever the game state changes (e.g. a move counter, a hash, a timestamp):

```json
{
  "version": "42",
  ...
}
```

If `version` stays the same for **60 minutes**, the GameRoom concludes the game is stuck and calls `DELETE /games/{sessionId}` automatically.

---

### 6. `DELETE /games/{sessionId}` — delete a session

Called when the game is stale (60-minute inactivity timeout) or when session creation fails partway through. Free all server-side resources for this session. Return any `2xx` (or even a `404` if it was already gone — the GameRoom ignores the response body).

---

## Optional REST endpoints

### `GET /game-options` — lobby configuration options

Called by the GameRoom when the room host opens the game-options screen. If this endpoint is not implemented (or returns an error), the GameRoom silently skips the options screen and starts the game with no options.

**Response body:** a JSON array of option objects.

```json
[
  {
    "key": "hardMode",
    "labelKey": "gameOption.hardMode",
    "descriptionKey": "gameOption.hardMode.desc",
    "type": "BOOLEAN",
    "defaultValue": "false"
  },
  {
    "key": "color",
    "labelKey": "gameOption.color",
    "choices": ["red", "blue", "green"],
    "defaultValue": "red"
  },
  {
    "key": "rounds",
    "labelKey": "gameOption.rounds",
    "defaultValue": "5"
  }
]
```

The widget rendered in the lobby is determined by the fields present:

| Fields | Widget |
|--------|--------|
| `type: "BOOLEAN"` | Checkbox |
| `choices` array present | Dropdown |
| neither | Text input |

`labelKey` and `descriptionKey` are translation keys resolved from the game's own `GET /i18n/{lang}.json` file. The selected values are sent back as the `gameOptions` map in `POST /games`.

---

## Profile pictures

Players choose a profile picture in the lobby. The selected index is passed to your game as `profilePic` (an integer) in the `POST /games/{sessionId}/players` call.

To render the picture, fetch it directly from the GameRoom:

```
GET http://<gameroom-base-url>/profile-pic/{index}
```

Returns a **100 × 100 PNG**. The image is publicly cacheable for 24 hours (`Cache-Control: public, max-age=86400`), so you can cache it client-side.

---

## Chat window

The GameRoom lobby provides a built-in chat for players waiting in the room. **Games themselves do not need to implement chat** — the lobby handles it before the game starts.

If you want to embed a similar chat inside your game, the GameRoom chat REST API works as follows:

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/chat/{roomId}` | Send a message |
| `GET`  | `/chat/{roomId}` | Fetch all messages |

**Send message body:**
```json
{ "sender": "Alice", "message": "<encrypted text>" }
```

Messages are encrypted with the room ID as the cipher key (see `ChatCipher`). The client polls `GET /chat/{roomId}` every 500 ms.

---

## Summary checklist

| Requirement | Detail |
|-------------|--------|
| `POST /games` | Returns `{ "sessionId": "..." }` |
| `POST /games/{id}/players` | Accepts `id`, `name`, `profilePic` |
| `POST /games/{id}` | Starts game, no body needed |
| `GET /games/{id}` | Returns `2xx` while session is alive |
| `GET /gamestates/{id}` | Returns JSON with a `version` field |
| `DELETE /games/{id}` | Cleans up session resources |
| `GET /game-options` | Optional — returns array of option objects for the lobby |
| Redirect params | Read `?sessionid=`, `?playerid=`, `?locale=` on load |
| Profile pictures | Fetch from `GET <gameroom>/profile-pic/{index}` |
| Port registration | Add `baseUrl` to `games.yaml` |

</details>

---


# Preparing your Raspberry Pi from scratch

<details>
<summary>Preparing your Raspberry Pi from scratch</summary>

The scripts in this repository do most of the work. This guide covers the few steps they
don't, in order: getting Ubuntu on the Pi, reaching it over SSH, and installing Java.

## 1. Install Ubuntu Server

Use [Raspberry Pi Imager](https://www.raspberrypi.com/software/) to write **Ubuntu Server
26.04 LTS (64-bit)** to the SD card. In the imager's settings, create the user `ubuntu` and
turn on SSH. The scripts assume that user and a 64-bit (arm64) system; the current server
is a Raspberry Pi 4.

Find the Pi's address on your network, for example in your router's device list or with
`hostname -I` on the Pi itself.

## 2. Set up SSH with a deploy key

On your laptop, create a key used only for the Pi and copy it over:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/pi_deploy_key
ssh-copy-id -i ~/.ssh/pi_deploy_key.pub ubuntu@<PI_IP>
```

Add an alias to `~/.ssh/config`. Every script connects to `my-pi` (home network) and falls
back to `my-pi-ext` (Cloudflare Tunnel, see [Deploying from outside your local
network](#deploying-from-outside-your-local-network-ssh-over-cloudflare-tunnel)):

```
Host my-pi
    HostName <PI_IP>
    User ubuntu
    IdentityFile ~/.ssh/pi_deploy_key
```

Check it with `ssh my-pi`. The scripts run `sudo` on the Pi without a password, which is how
Ubuntu's `ubuntu` user is set up by default.

## 3. Install Java 25

The game room and the games are built for Java 25, and the setup scripts expect it to be
installed already:

```bash
ssh my-pi "sudo apt-get update && sudo apt-get install -y openjdk-25-jre-headless"
```

## 4. Firewall

Only SSH needs to be reachable. Everything public arrives through the Cloudflare Tunnel,
which connects to nginx from the Pi itself, so no web port has to be open:

```bash
ssh my-pi "sudo ufw allow OpenSSH && sudo ufw enable"
```

A tunnel is used instead of port forwarding because many home connections sit behind
CGNAT: the address the internet sees is shared with other customers, so forwarded ports
never reach the Pi.

## 5. Run the setup and deploy

1. Optionally create `deploy.local.conf` for a fixed domain (see [First-time Pi
   setup](#first-time-pi-setup)). Without it you get a temporary `trycloudflare.com` address.
2. `./setup-pi.sh` installs and configures nginx, the shared Tomcat (`setup-tomcat.sh`),
   cloudflared and the `gameroom` service, and caps the system logs at 300 MB.
3. `./deploy.sh` builds the game room and starts it.
4. `./set-admin-password.sh` creates a new password for the `admin` login, prints it once and
   restarts the game room. Write it down: only its hash is stored.
5. Deploy each game from its own repository with its `deploy.sh`, which puts its WAR into
   Tomcat.

When it's done, this is what runs on the Pi:

| Service | Listens on | Serves |
|---|---|---|
| `cloudflared` | outgoing only | forwards the public domains to nginx |
| `nginx` | port 80 | sends `/keezen/` and `/qwixx/` to Tomcat and everything else to the game room |
| `gameroom` | port 4100 | the lobby and rooms |
| `tomcat10` | `127.0.0.1:8090` | Keezen and Qwixx |

After a reboot, Keezen and Qwixx take about a minute to answer while Tomcat deploys them.

## 6. Keep it up to date

`./update-server.sh` installs package updates and the latest cloudflared, which was
installed as a plain file and is never updated by `apt`. It refuses to run while rooms are
open, because an update that restarts Java or Tomcat loses every room and running game.

```bash
./update-server.sh            # update, and report if a reboot is needed
./update-server.sh --reboot   # reboot afterwards if the update asks for it
./update-server.sh --force    # update even while rooms are open
```

To move to a new Ubuntu release, run the upgrade over the home network, never through the
tunnel, because it restarts cloudflared:

```bash
ssh my-pi
sudo do-release-upgrade
```

When it asks about a configuration file you changed, keep your currently installed version.
Afterwards, check that `java -version` still reports 25 and that all four services above
are running.

## 7. When something is wrong

- `./diagnose.sh` shows the service status, recent errors, the installed config, the Java
  version, listening ports, disk space and memory.
- Logs: `ssh my-pi journalctl -u gameroom -f` for the game room, and
  `journalctl -u tomcat10 -f` for the games.
- `./get-tunnel-url.sh` prints the current address when you use a temporary tunnel.

</details>
