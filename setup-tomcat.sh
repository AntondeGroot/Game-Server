#!/bin/bash
set -e

# Shared Tomcat for the games that deploy as a WAR (Keezen and Qwixx). Each app drops <name>.war into
# /var/lib/tomcat10/webapps and is served at /<name>; nginx forwards /<name>/ without stripping it.
# Standalone so it can run on a live Pi: unlike setup-pi.sh it never rewrites the nginx site file.

TARGET="${1:-my-pi}"
SSH="ssh -i ~/.ssh/pi_deploy_key $TARGET"

# 80, 4100 and 8080/8081 (PhotoKeeper blue/green) are taken; 4200/4300 were standalone Keezen/Qwixx.
TOMCAT_PORT=8090
JAVA_HOME_DIR=/usr/lib/jvm/java-25-openjdk-arm64

echo "📦 Installing Tomcat 10.1..."
# Spring Boot 3.x targets Jakarta EE 10, which is Tomcat 10.1 — the version Ubuntu ships as tomcat10.
$SSH "sudo apt-get install -y --no-install-recommends tomcat10"

echo "⚙️  Binding Tomcat to localhost:$TOMCAT_PORT..."
# Only nginx should reach it. Disabling the shutdown port is safe: systemd stops Tomcat by signal.
$SSH "sudo sed -i \
        -e 's|<Connector port=\"8080\"|<Connector port=\"$TOMCAT_PORT\" address=\"127.0.0.1\"|' \
        -e 's|<Server port=\"8005\"|<Server port=\"-1\"|' \
        /etc/tomcat10/server.xml && \
      sudo grep -q 'port=\"$TOMCAT_PORT\" address=\"127.0.0.1\"' /etc/tomcat10/server.xml"

echo "⚙️  Configuring the Tomcat JVM..."
# The apps are compiled for Java 25, so pin it rather than let Tomcat pick whatever JDK it finds.
# Heap and metaspace are shared by every deployed app (Keezen + Qwixx ran in ~525 MB as two JVMs).
$SSH "sudo tee /etc/default/tomcat10 > /dev/null << 'EOF'
JAVA_HOME=$JAVA_HOME_DIR
JAVA_OPTS=\"-Djava.awt.headless=true -Xmx384m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError\"
EOF"
$SSH "sudo mkdir -p /etc/systemd/system/tomcat10.service.d && \
      sudo tee /etc/systemd/system/tomcat10.service.d/restart.conf > /dev/null << 'EOF'
[Service]
Restart=on-failure
EOF"

$SSH "sudo systemctl daemon-reload && sudo systemctl enable tomcat10 && sudo systemctl restart tomcat10"

echo "✅ Tomcat is running on 127.0.0.1:$TOMCAT_PORT. Apps deploy into /var/lib/tomcat10/webapps."
