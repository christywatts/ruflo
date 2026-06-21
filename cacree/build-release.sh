#!/usr/bin/env bash
# build-release.sh — Build Cacree release AAB ready for Google Play
# Run from the cacree/ directory: bash build-release.sh
set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓ $*${NC}"; }
warn() { echo -e "${YELLOW}⚠ $*${NC}"; }
fail() { echo -e "${RED}✗ $*${NC}"; exit 1; }
step() { echo -e "\n${GREEN}── Step $* ──────────────────────────────${NC}"; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "  ██████╗ █████╗  ██████╗██████╗ ███████╗███████╗"
echo "  ██╔════╝██╔══██╗██╔════╝██╔══██╗██╔════╝██╔════╝"
echo "  ██║     ███████║██║     ██████╔╝█████╗  █████╗  "
echo "  ██║     ██╔══██║██║     ██╔══██╗██╔══╝  ██╔══╝  "
echo "  ╚██████╗██║  ██║╚██████╗██║  ██║███████╗███████╗"
echo "   ╚═════╝╚═╝  ╚═╝ ╚═════╝╚═╝  ╚═╝╚══════╝╚══════╝"
echo "  Release AAB Builder"
echo ""

# ── STEP 1: Check Java ───────────────────────────────────────
step "1/5 — Check Java (17+ required)"
if ! command -v java &>/dev/null; then
  fail "Java not found. Install JDK 17: https://adoptium.net"
fi
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
[[ "$JAVA_VER" -ge 17 ]] || fail "Java 17+ required (found $JAVA_VER). Install from https://adoptium.net"
ok "Java $JAVA_VER"

# ── STEP 2: Android SDK ──────────────────────────────────────
step "2/5 — Android SDK (API 35)"
if [[ -z "$ANDROID_HOME" ]]; then
  # Common default paths
  for candidate in \
      "$HOME/Library/Android/sdk" \
      "$HOME/Android/Sdk" \
      "/usr/local/lib/android/sdk" \
      "/opt/android-sdk"; do
    if [[ -d "$candidate/platforms/android-35" ]]; then
      export ANDROID_HOME="$candidate"
      break
    fi
  done
fi

if [[ -z "$ANDROID_HOME" ]] || [[ ! -d "$ANDROID_HOME/platforms/android-35" ]]; then
  warn "Android SDK (API 35) not found."
  echo ""
  echo "  Install Android Studio (easiest):  https://developer.android.com/studio"
  echo "  Then open the SDK Manager and install:"
  echo "    • Android SDK Platform 35"
  echo "    • Android SDK Build-Tools 35.0.0"
  echo ""
  echo "  Or install command-line tools only:"
  echo "    https://developer.android.com/studio#command-line-tools-only"
  echo ""
  echo "  Then re-run this script."
  exit 1
fi
ok "ANDROID_HOME=$ANDROID_HOME"

# ── STEP 3: Keystore ─────────────────────────────────────────
step "3/5 — Signing keystore"
KEYSTORE_FILE="$SCRIPT_DIR/cacree-release.jks"
PROPS_FILE="$SCRIPT_DIR/keystore.properties"

if [[ -f "$PROPS_FILE" ]]; then
  ok "keystore.properties already exists"
else
  if [[ ! -f "$KEYSTORE_FILE" ]]; then
    warn "No keystore found — generating one now."
    echo ""
    echo "  You will be asked for:"
    echo "    1. A keystore password (remember this!)"
    echo "    2. Your name/org info"
    echo ""
    keytool -genkey -v \
      -keystore "$KEYSTORE_FILE" \
      -keyalg RSA -keysize 2048 -validity 10000 \
      -alias cacree \
      -dname "CN=Cacree, OU=Mobile, O=Cacree, L=Nairobi, ST=Nairobi, C=KE" \
      2>&1 || {
        echo "  Re-running with interactive prompts..."
        keytool -genkey -v \
          -keystore "$KEYSTORE_FILE" \
          -keyalg RSA -keysize 2048 -validity 10000 \
          -alias cacree
      }
    ok "Keystore created: $KEYSTORE_FILE"
    warn "BACK UP this file — losing it means you can never update the app on Play Store!"
  else
    ok "Keystore already exists: $KEYSTORE_FILE"
  fi

  # Prompt for passwords
  echo ""
  read -s -p "  Enter keystore password: " KS_PASS; echo
  read -s -p "  Enter key password (same if unsure): " K_PASS; echo

  cat > "$PROPS_FILE" <<PROPS
storeFile=../cacree-release.jks
storePassword=$KS_PASS
keyAlias=cacree
keyPassword=$K_PASS
PROPS
  ok "keystore.properties created"
fi

# ── STEP 4: Build ────────────────────────────────────────────
step "4/5 — Build release AAB"
chmod +x gradlew
echo "  Running: ./gradlew bundleRelease"
./gradlew bundleRelease --stacktrace 2>&1 | grep -E "^(BUILD|FAILED|ERROR|> Task|Caused by)" || \
./gradlew bundleRelease

AAB="$SCRIPT_DIR/app/build/outputs/bundle/release/app-release.aab"
[[ -f "$AAB" ]] || fail "AAB not found at expected path. Check output above."
ok "AAB built: $AAB ($(du -h "$AAB" | cut -f1))"

# ── STEP 5: Summary ──────────────────────────────────────────
step "5/5 — Done!"
echo ""
echo "  Your AAB is ready at:"
echo "  $AAB"
echo ""
echo "  Upload to Google Play:"
echo "  1. Go to https://play.google.com/console"
echo "  2. Create app → com.cacree.app"
echo "  3. Testing → Internal testing → Create release"
echo "  4. Upload the .aab file above"
echo "  5. Fill out the SMS Permissions Declaration Form"
echo "     (explain: reads bank SMS to auto-track transactions)"
echo ""
echo -e "  ${GREEN}Good luck on the Play Store! 🚀${NC}"
echo ""
