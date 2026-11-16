export PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
export HOME="${HOME:-/data/data/com.termux/files/home}"
TJU_TOTAL=5

xp() {
  local p="$1"
  p="${p/#\~/$HOME}"
  p="${p/#\$HOME/$HOME}"
  p="${p/#\$PREFIX/$PREFIX}"
  printf '%s' "$p"
}

log() { printf '%s\n' "$*"; }
chk() { log "##TJU_CHECK## $1 $2 $3"; eval "ST_$1='$2'"; }

find_java() {
  local c j
  if [ -n "$JAVA_HOME_OVERRIDE" ]; then
    c="$(xp "$JAVA_HOME_OVERRIDE")"
    [ -x "$c/bin/java" ] && { echo "$c"; return 0; }
    return 1
  fi
  for c in "$PREFIX/lib/jvm/java-17-openjdk" "$PREFIX/lib/jvm/java-21-openjdk" "$PREFIX/lib/jvm/java-11-openjdk" "$JAVA_HOME"; do
    [ -n "$c" ] && [ -x "$c/bin/java" ] && { echo "$c"; return 0; }
  done
  j="$(command -v java 2>/dev/null)" || return 1
  dirname "$(dirname "$(readlink -f "$j")")"
}

run_checks() {
  local jv sd pl bt avail mem
  JH="$(find_java)"
  if [ -n "$JH" ]; then
    jv="$("$JH/bin/java" -version 2>&1 | grep -m1 'version' | tr -d '"')"
    chk java OK "$jv"
    export JAVA_HOME="$JH"
    export PATH="$JH/bin:$PATH"
  else
    chk java FAIL "Java não encontrado"
  fi
  command -v unzip >/dev/null 2>&1 && chk unzip OK "$(command -v unzip)" || chk unzip FAIL "não instalado"
  command -v git >/dev/null 2>&1 && chk git OK "$(command -v git)" || chk git FAIL "não instalado"
  command -v curl >/dev/null 2>&1 && chk curl OK "$(command -v curl)" || chk curl FAIL "não instalado"
  [ -x "$PREFIX/bin/aapt2" ] && chk aapt2 OK "$PREFIX/bin/aapt2" || chk aapt2 FAIL "não instalado"
  sd="$(xp "$SDK_DIR")"
  if [ -d "$sd" ]; then
    chk sdk OK "$sd"
    pl="$(ls "$sd/platforms" 2>/dev/null | tr '\n' ' ')"
    [ -n "$pl" ] && chk platforms OK "$pl" || chk platforms FAIL "nenhuma plataforma em $sd/platforms"
    bt="$(ls "$sd/build-tools" 2>/dev/null | tr '\n' ' ')"
    [ -n "$bt" ] && chk buildtools OK "$bt" || chk buildtools WARN "nenhuma pasta build-tools (o Gradle pode baixar)"
  else
    chk sdk FAIL "pasta não existe: $sd"
    chk platforms FAIL "sem SDK"
    chk buildtools FAIL "sem SDK"
  fi
  if ls "$(xp "$DOWNLOAD_DIR")" >/dev/null 2>&1; then
    chk storage OK "$(xp "$DOWNLOAD_DIR")"
  else
    chk storage FAIL "sem acesso a $(xp "$DOWNLOAD_DIR")"
  fi
  avail="$(df -Pk "$HOME" 2>/dev/null | awk 'NR==2{print int($4/1024)}')"
  if [ -n "$avail" ] && [ "$avail" -lt 400 ]; then chk disk FAIL "${avail} MB livres"
  elif [ -n "$avail" ] && [ "$avail" -lt 1500 ]; then chk disk WARN "${avail} MB livres"
  else chk disk OK "${avail:-?} MB livres"; fi
  mem="$(awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo 2>/dev/null)"
  if [ -n "$mem" ] && [ "$mem" -lt 1200 ]; then chk memory WARN "${mem} MB de RAM livre"
  else chk memory OK "${mem:-?} MB de RAM livre"; fi
  if command -v curl >/dev/null 2>&1 && curl -sI -m 8 -o /dev/null https://github.com 2>/dev/null; then
    chk net OK "github.com acessível"
  else
    chk net WARN "sem acesso a github.com"
  fi
}

if [ "$TJU_MODE" = "preflight" ]; then
  run_checks
  log "##TJU_DONE## preflight"
  exit 0
fi

set -o pipefail
TJU_DIR="$HOME/.tju/jobs/$TJU_ID"
mkdir -p "$TJU_DIR"
echo $$ > "$TJU_DIR/pid"
: > "$TJU_DIR/artifacts"
TJU_TOKEN="$(cat "$TJU_DIR/token" 2>/dev/null)"
rm -f "$TJU_DIR/token"
START_TS="$(date +%s)"

status() { printf '%s\n' "$*" > "$TJU_DIR/status"; }
step() { log ""; log "##TJU_STEP## $1/$TJU_TOTAL $2"; status "RUNNING $1/$TJU_TOTAL $2"; }
warn() { log "##TJU_WARN## $1 ${2:-}"; }
cleanup() { command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock >/dev/null 2>&1; return 0; }
fail() { log "##TJU_FAIL## $1 ${2:-}"; status "FAILED $1"; cleanup; exit 1; }
mask_token() { if [ -n "$TJU_TOKEN" ]; then sed "s#$TJU_TOKEN#***#g"; else cat; fi; }
trap 'status "CANCELLED"; log "##TJU_FAIL## CANCELLED"; cleanup; exit 143' TERM INT

command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1

step 1 "Verificando ambiente"
run_checks
[ "$ST_java" = OK ] || fail JAVA_MISSING
if [ "$SRC_MODE" = zip ]; then
  [ "$ST_unzip" = OK ] || fail TOOL_MISSING unzip
else
  [ "$ST_git" = OK ] || [ "$ST_curl" = OK ] || fail TOOL_MISSING git
fi
[ "$ST_storage" = OK ] || fail STORAGE_PERMISSION
[ "$ST_disk" != FAIL ] || fail DISK_LOW

step 2 "Obtendo código-fonte"
WORK_BASE="$(xp "$WORK_BASE")"
DOWNLOAD_DIR="$(xp "$DOWNLOAD_DIR")"
OUT_DIR="$(xp "$OUT_DIR")"
mkdir -p "$WORK_BASE" || fail WORKDIR_FAILED "$WORK_BASE"

if [ "$SRC_MODE" = zip ]; then
  ZP="$ZIP_NAME"
  case "$ZP" in
    /*) ;;
    "~"*|"\$"*) ZP="$(xp "$ZP")" ;;
    *) ZP="$DOWNLOAD_DIR/$ZP" ;;
  esac
  if [ ! -f "$ZP" ] && [ -f "$ZP.zip" ]; then ZP="$ZP.zip"; fi
  if [ ! -f "$ZP" ]; then
    log "ZIPs encontrados em $DOWNLOAD_DIR:"
    ls -1t "$DOWNLOAD_DIR"/*.zip 2>/dev/null | head -8 | sed 's#.*/#  - #'
    fail ZIP_NOT_FOUND "$ZIP_NAME"
  fi
  NAME="$(basename "$ZP" .zip)"
  DEST="$WORK_BASE/$NAME"
  rm -rf "$DEST"
  mkdir -p "$DEST"
  log "Extraindo $ZP"
  unzip -q -o "$ZP" -d "$DEST" || fail ZIP_CORRUPT "$ZP"
else
  NAME="$(basename "$GIT_URL" .git)"
  DEST="$WORK_BASE/$NAME"
  rm -rf "$DEST"
  GOT=0
  if [ "$ST_git" = OK ]; then
    CL_URL="$GIT_URL"
    [ -n "$TJU_TOKEN" ] && CL_URL="${GIT_URL/https:\/\//https://x-access-token:$TJU_TOKEN@}"
    log "Clonando $GIT_URL${GIT_BRANCH:+ (branch $GIT_BRANCH)}"
    if [ -n "$GIT_BRANCH" ]; then
      GIT_TERMINAL_PROMPT=0 git clone --depth 1 -b "$GIT_BRANCH" "$CL_URL" "$DEST" 2>&1 | mask_token
    else
      GIT_TERMINAL_PROMPT=0 git clone --depth 1 "$CL_URL" "$DEST" 2>&1 | mask_token
    fi
    if [ "${PIPESTATUS[0]}" -eq 0 ]; then
      GOT=1
      git -C "$DEST" remote set-url origin "$GIT_URL" >/dev/null 2>&1
    fi
  fi
  if [ "$GOT" -eq 0 ] && [ -z "$TJU_TOKEN" ] && [ "$ST_curl" = OK ] && [ "$ST_unzip" = OK ]; then
    if [ -n "$GIT_BRANCH" ]; then ZURL="${GIT_URL%.git}/archive/refs/heads/$GIT_BRANCH.zip"; else ZURL="${GIT_URL%.git}/archive/HEAD.zip"; fi
    log "Tentando baixar ZIP: $ZURL"
    rm -rf "$DEST"; mkdir -p "$DEST"
    if curl -fL --retry 2 -o "$TJU_DIR/src.zip" "$ZURL" && unzip -q -o "$TJU_DIR/src.zip" -d "$DEST"; then
      GOT=1
    fi
    rm -f "$TJU_DIR/src.zip"
  fi
  [ "$GOT" -eq 1 ] || fail CLONE_FAILED "$GIT_URL"
fi

find_root() {
  local d="$1" c
  if [ -f "$d/gradlew" ] || ls "$d"/settings.gradle* >/dev/null 2>&1; then echo "$d"; return; fi
  c="$(find "$d" -maxdepth 3 \( -name gradlew -o -name settings.gradle -o -name settings.gradle.kts \) 2>/dev/null | awk '{print length($0) " " $0}' | sort -n | head -1 | cut -d' ' -f2-)"
  [ -n "$c" ] && dirname "$c"
}
ROOT="$(find_root "$DEST")"
[ -n "$ROOT" ] || fail NOT_GRADLE_PROJECT "$DEST"
cd "$ROOT" || fail NOT_GRADLE_PROJECT "$ROOT"
echo "$ROOT" > "$TJU_DIR/workdir"
log "Projeto: $ROOT"

step 3 "Preparando projeto"
unset STORE_PASSWORD KEY_PASSWORD KEY_ALIAS
if [ -n "$EXTRA_FILES" ]; then
  ED="$(xp "$EXTRAS_DIR")"
  for f in $EXTRA_FILES; do
    [ -f "$ED/$f" ] || fail EXTRA_MISSING "$ED/$f"
    cp -f "$ED/$f" "$ROOT/" || fail EXTRA_MISSING "$ED/$f"
    chmod 600 "$ROOT/$f"
    log "Copiado: $f"
  done
fi

JKS="$(ls *.jks *.keystore 2>/dev/null | head -1)"
if [ -n "$JKS" ] && command -v keytool >/dev/null 2>&1 && [ -f .store_password.txt ]; then
  KALIAS=""
  [ -f .key_alias.txt ] && KALIAS="$(tr -d '\r\n' < .key_alias.txt)"
  if keytool -list -keystore "$JKS" -storepass:file .store_password.txt ${KALIAS:+-alias "$KALIAS"} >/dev/null 2>&1; then
    log "Keystore verificada: $JKS"
  else
    fail KEYSTORE_INVALID "$JKS"
  fi
fi

GRADLE_CMD=""
if [ -f gradlew ]; then
  chmod +x gradlew
  if [ -f gradle/wrapper/gradle-wrapper.jar ]; then
    GRADLE_CMD="./gradlew"
  elif command -v gradle >/dev/null 2>&1; then
    warn WRAPPER_JAR_MISSING
    GRADLE_CMD="gradle"
  else
    fail WRAPPER_JAR_MISSING
  fi
elif command -v gradle >/dev/null 2>&1; then
  warn NO_GRADLEW
  GRADLE_CMD="gradle"
else
  fail NO_GRADLEW
fi

SETTINGS_FILE="$(ls settings.gradle settings.gradle.kts 2>/dev/null | head -1)"
has_app_plugin() { grep -qE "android\.application|android-application" "$1" 2>/dev/null; }
CANDS=""
[ -n "$SETTINGS_FILE" ] && CANDS="$(grep -E '^[[:space:]]*include' "$SETTINGS_FILE" 2>/dev/null | grep -oE "[\"'][^\"']+[\"']" | tr -d "\"'" | sed 's/^://')"
IS_ANDROID=0
MOD="$MODULE"
if [ -n "$MOD" ]; then
  MOD="${MOD#:}"
  MD="${MOD//://}"
  if [ ! -d "$MD" ]; then
    log "Módulos encontrados: $(echo $CANDS)"
    fail MODULE_NOT_FOUND "$MOD"
  fi
  for b in "$MD/build.gradle" "$MD/build.gradle.kts"; do has_app_plugin "$b" && IS_ANDROID=1; done
else
  for m in $CANDS; do
    d="${m//://}"
    for b in "$d/build.gradle" "$d/build.gradle.kts"; do
      if has_app_plugin "$b"; then MOD="$m"; IS_ANDROID=1; break 2; fi
    done
  done
  if [ "$IS_ANDROID" -eq 0 ]; then
    for b in build.gradle build.gradle.kts; do has_app_plugin "$b" && IS_ANDROID=1; done
  fi
fi
if [ "$IS_ANDROID" -eq 1 ]; then log "Tipo: aplicativo Android${MOD:+ (módulo $MOD)}"; else log "Tipo: projeto Java/mod (JAR)"; fi

case "$OUTPUT_KIND" in
  RELEASE) TASK=assembleRelease ;;
  DEBUG) TASK=assembleDebug ;;
  JAR) TASK=build ;;
  *)
    if [ "$IS_ANDROID" -eq 1 ]; then
      if [ -n "$JKS" ]; then TASK=assembleRelease; else TASK=assembleDebug; fi
    else
      TASK=build
    fi ;;
esac
log "Tarefa: $TASK"

GRADLE_EXTRA=""
if [ "$IS_ANDROID" -eq 1 ]; then
  export ANDROID_HOME="$(xp "$SDK_DIR")"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  [ -d "$ANDROID_HOME" ] || fail SDK_MISSING "$ANDROID_HOME"
  ls "$ANDROID_HOME/platforms" 2>/dev/null | grep -q . || fail SDK_NO_PLATFORM "$ANDROID_HOME/platforms"
  for b in "${MOD:+${MOD//://}/}build.gradle" "${MOD:+${MOD//://}/}build.gradle.kts"; do
    [ -f "$b" ] || continue
    CS="$(grep -hoE 'compileSdk(Version)?[ =(]+[0-9]+' "$b" | grep -oE '[0-9]+$' | head -1)"
    if [ -n "$CS" ] && [ ! -d "$ANDROID_HOME/platforms/android-$CS" ]; then warn PLATFORM_MISSING "android-$CS"; fi
  done
  if [ "$USE_AAPT2" = "1" ]; then
    [ -x "$PREFIX/bin/aapt2" ] || fail AAPT2_MISSING
    GRADLE_EXTRA="-Pandroid.aapt2FromMavenOverride=$PREFIX/bin/aapt2"
  fi
fi

step 4 "Compilando com Gradle ($TASK)"
MARK="$TJU_DIR/mark"
touch "$MARK"
sleep 1
TARGET="${MOD:+:$MOD:}$TASK"
log "Comando: $GRADLE_CMD $TARGET --no-daemon --console=plain $GRADLE_EXTRA $GRADLE_ARGS"
$GRADLE_CMD "$TARGET" --no-daemon --console=plain $GRADLE_EXTRA $GRADLE_ARGS
RC=$?
if [ "$RC" -ne 0 ]; then
  [ "$RC" -eq 137 ] && fail OOM_KILLED
  fail GRADLE_FAILED "$RC"
fi

step 5 "Copiando resultado"
mkdir -p "$OUT_DIR" 2>/dev/null
if [ "$IS_ANDROID" -eq 1 ]; then
  FILES="$(find . -path '*/build/outputs/apk/*' -name '*.apk' -newer "$MARK" 2>/dev/null)"
else
  FILES="$(find . -path '*/build/libs/*' -name '*.jar' -newer "$MARK" 2>/dev/null | grep -vE -- '-(sources|javadoc)\.jar$')"
fi
[ -n "$FILES" ] || fail NO_OUTPUT "$TASK"
COUNT=0
while IFS= read -r f; do
  [ -n "$f" ] || continue
  bn="$(basename "$f")"
  case "$bn" in *unsigned*) warn UNSIGNED_APK "$bn" ;; esac
  if ! cp -f "$f" "$OUT_DIR/$bn" 2>/dev/null; then
    bn2="${bn%.*}-$(date +%H%M%S).${bn##*.}"
    cp -f "$f" "$OUT_DIR/$bn2" 2>/dev/null || fail COPY_FAILED "$OUT_DIR"
    bn="$bn2"
  fi
  log "Salvo: $OUT_DIR/$bn"
  echo "$OUT_DIR/$bn" >> "$TJU_DIR/artifacts"
  COUNT=$((COUNT+1))
done <<< "$FILES"

END_TS="$(date +%s)"
status "SUCCESS"
log "##TJU_DONE## $COUNT arquivo(s) em $((END_TS-START_TS))s"
cleanup
exit 0
