export PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
export HOME="${HOME:-/data/data/com.termux/files/home}"
export DEBIAN_FRONTEND=noninteractive
TJU_TOTAL=4

xp() {
  local p="$1"
  p="${p/#\~/$HOME}"
  p="${p/#\$HOME/$HOME}"
  p="${p/#\$PREFIX/$PREFIX}"
  printf '%s' "$p"
}

log() { printf '%s\n' "$*"; }

set -o pipefail
TJU_DIR="$HOME/.tju/jobs/$TJU_ID"
mkdir -p "$TJU_DIR"
echo $$ > "$TJU_DIR/pid"
: > "$TJU_DIR/artifacts"
START_TS="$(date +%s)"

status() { printf '%s\n' "$*" > "$TJU_DIR/status"; }
step() { log ""; log "##TJU_STEP## $1/$TJU_TOTAL $2"; status "RUNNING $1/$TJU_TOTAL $2"; }
warn() { log "##TJU_WARN## $1 ${2:-}"; }
cleanup() { command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock >/dev/null 2>&1; return 0; }
fail() { log "##TJU_FAIL## $1 ${2:-}"; status "FAILED $1"; cleanup; exit 1; }
trap 'status "CANCELLED"; log "##TJU_FAIL## CANCELLED"; cleanup; exit 143' TERM INT

command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock >/dev/null 2>&1

find_java() {
  local c j
  if [ -n "$JAVA_HOME_OVERRIDE" ]; then
    c="$(xp "$JAVA_HOME_OVERRIDE")"
    [ -x "$c/bin/java" ] && { echo "$c"; return 0; }
    return 1
  fi
  for c in "$PREFIX/lib/jvm/java-21-openjdk" "$PREFIX/lib/jvm/java-17-openjdk" "$PREFIX/lib/jvm/java-11-openjdk" "$JAVA_HOME"; do
    [ -n "$c" ] && [ -x "$c/bin/java" ] && { echo "$c"; return 0; }
  done
  j="$(command -v java 2>/dev/null)" || return 1
  dirname "$(dirname "$(readlink -f "$j")")"
}

need() {
  local cmd="$1" pkgname="$2"
  command -v "$cmd" >/dev/null 2>&1 && return 0
  log "Instalando $pkgname no Termux…"
  pkg install -y "$pkgname" < /dev/null 2>&1 | tail -n 3
  command -v "$cmd" >/dev/null 2>&1 && return 0
  log "Atualizando a lista de pacotes e tentando de novo…"
  pkg update -y < /dev/null 2>&1 | tail -n 2
  pkg install -y "$pkgname" < /dev/null 2>&1 | tail -n 3
  command -v "$cmd" >/dev/null 2>&1 || fail TOOL_INSTALL_FAILED "$pkgname"
}

step 1 "Verificando ferramentas"
DOWNLOAD_DIR="$(xp "$DOWNLOAD_DIR")"
OUT_DIR="$(xp "$OUT_DIR")"
WORK_BASE="$(xp "$WORK_BASE")"
ls "$DOWNLOAD_DIR" >/dev/null 2>&1 || fail STORAGE_PERMISSION
mkdir -p "$OUT_DIR" 2>/dev/null
[ -d "$OUT_DIR" ] && [ -w "$OUT_DIR" ] || fail COPY_FAILED "$OUT_DIR"
mkdir -p "$WORK_BASE" || fail WORKDIR_FAILED "$WORK_BASE"

EXT="${DEC_INPUT##*.}"
EXT="$(printf '%s' "$EXT" | tr 'A-Z' 'a-z')"
MODE="$DEC_MODE"
case "$EXT" in
  apk|jar|aar|dex|class|zip) ;;
  *) fail DEC_UNSUPPORTED "$EXT" ;;
esac
case "$MODE" in
  SMALI|BOTH)
    if [ "$EXT" != apk ]; then
      warn DEC_APK_ONLY "$EXT"
      MODE=JAVA
    fi
    ;;
  DEX2JAR)
    case "$EXT" in apk|dex) ;; *) fail DEC_MODE_UNSUPPORTED "$EXT" ;; esac
    ;;
esac

case "$MODE" in
  JAVA) need jadx jadx ;;
  SMALI) need apktool apktool ;;
  BOTH) need jadx jadx; need apktool apktool ;;
  DEX2JAR) need d2j-dex2jar dex2jar ;;
  EXTRACT) need unzip unzip ;;
esac
[ "$MODE" = DEX2JAR ] || need zip zip
[ "$EXT" = apk ] || [ "$EXT" = jar ] || [ "$EXT" = aar ] || [ "$EXT" = zip ] && need unzip unzip

JH="$(find_java)"
if [ -z "$JH" ] && [ "$MODE" != EXTRACT ]; then
  need java openjdk-21
  JH="$(find_java)"
fi
if [ -n "$JH" ]; then
  export JAVA_HOME="$JH"
  export PATH="$JH/bin:$PATH"
  log "Java: $("$JH/bin/java" -version 2>&1 | grep -m1 'version' | tr -d '"')"
elif [ "$MODE" != EXTRACT ]; then
  fail JAVA_MISSING
fi
avail="$(df -Pk "$HOME" 2>/dev/null | awk 'NR==2{print int($4/1024)}')"
[ -n "$avail" ] && [ "$avail" -lt 200 ] && fail DISK_LOW

step 2 "Preparando o arquivo"
IN="$DEC_INPUT"
case "$IN" in
  /*) ;;
  "~"*|"\$"*) IN="$(xp "$IN")" ;;
  *) IN="$DOWNLOAD_DIR/$IN" ;;
esac
if [ ! -f "$IN" ]; then
  log "Arquivos recentes em $DOWNLOAD_DIR:"
  ls -1t "$DOWNLOAD_DIR"/*.apk "$DOWNLOAD_DIR"/*.jar "$DOWNLOAD_DIR"/*.aar "$DOWNLOAD_DIR"/*.dex "$DOWNLOAD_DIR"/*.zip 2>/dev/null | head -8 | sed 's#.*/#  - #'
  fail DEC_INPUT_NOT_FOUND "$DEC_INPUT"
fi
BN="$(basename "$IN")"
NAME="${BN%.*}"
NAME="$(printf '%s' "$NAME" | tr -c 'A-Za-z0-9._-' '_')"
WORK="$WORK_BASE/$NAME"
rm -rf "$WORK"
mkdir -p "$WORK/in" "$WORK/out" || fail WORKDIR_FAILED "$WORK"
SIZE_MB="$(du -m "$IN" 2>/dev/null | awk '{print $1}')"
log "Arquivo: $BN (${SIZE_MB:-?} MB)"
case "$EXT" in
  apk|jar|aar|zip)
    unzip -tqq "$IN" >/dev/null 2>&1 || fail DEC_INPUT_CORRUPT "$BN"
    ;;
esac
cp -f "$IN" "$WORK/in/$NAME.$EXT" || fail COPY_FAILED "$WORK"
SRC="$WORK/in/$NAME.$EXT"
OUT="$WORK/out"

step 3 "Descompilando ($MODE)"
JADX_ARGS="-j 2"
[ "$DEC_DEOBF" = 1 ] && JADX_ARGS="$JADX_ARGS --deobf"
run_jadx() {
  log "jadx $JADX_ARGS -d out/java $BN"
  jadx $JADX_ARGS -d "$OUT/java" "$SRC" 2>&1 | tail -n 40
  local rc=${PIPESTATUS[0]}
  if [ "$rc" -eq 137 ]; then fail OOM_KILLED; fi
  if [ -z "$(find "$OUT/java" -type f 2>/dev/null | head -1)" ]; then fail DEC_TOOL_FAILED jadx; fi
  if [ "$rc" -ne 0 ]; then warn DEC_PARTIAL "jadx terminou com avisos/erros (código $rc)"; fi
}
run_apktool() {
  log "apktool d -f -o out/apktool $BN"
  apktool d -f -o "$OUT/apktool" "$SRC" 2>&1 | tail -n 40
  local rc=${PIPESTATUS[0]}
  if [ "$rc" -eq 137 ]; then fail OOM_KILLED; fi
  if [ "$rc" -ne 0 ] || [ -z "$(find "$OUT/apktool" -type f 2>/dev/null | head -1)" ]; then
    [ "$MODE" = BOTH ] && { warn DEC_PARTIAL "apktool falhou; o Java do jadx foi mantido"; return 0; }
    fail DEC_TOOL_FAILED apktool
  fi
}
case "$MODE" in
  JAVA) run_jadx ;;
  SMALI) run_apktool ;;
  BOTH) run_jadx; run_apktool ;;
  DEX2JAR)
    log "d2j-dex2jar -f -o $NAME.jar $BN"
    d2j-dex2jar -f -o "$OUT/$NAME.jar" "$SRC" 2>&1 | tail -n 30
    [ -s "$OUT/$NAME.jar" ] || fail DEC_TOOL_FAILED dex2jar
    ;;
  EXTRACT)
    unzip -q -o "$SRC" -d "$OUT/extraido" || fail DEC_INPUT_CORRUPT "$BN"
    ;;
esac

step 4 "Salvando o resultado"
JAVA_COUNT="$(find "$OUT" -name '*.java' 2>/dev/null | wc -l)"
SMALI_COUNT="$(find "$OUT" -name '*.smali' 2>/dev/null | wc -l)"
[ "$JAVA_COUNT" -gt 0 ] && log "Arquivos .java: $JAVA_COUNT"
[ "$SMALI_COUNT" -gt 0 ] && log "Arquivos .smali: $SMALI_COUNT"
if [ "$MODE" = DEX2JAR ]; then
  DEST="$OUT_DIR/$NAME-dex2jar.jar"
  cp -f "$OUT/$NAME.jar" "$DEST" || fail COPY_FAILED "$OUT_DIR"
elif [ "$DEC_AS_FOLDER" = 1 ]; then
  DEST="$OUT_DIR/$NAME-decompilado"
  rm -rf "$DEST"
  cp -r "$OUT" "$DEST" || fail COPY_FAILED "$OUT_DIR"
else
  DEST="$OUT_DIR/$NAME-decompilado.zip"
  rm -f "$DEST"
  (cd "$OUT" && zip -qr "$DEST" .) || fail COPY_FAILED "$OUT_DIR"
fi
log "Salvo: $DEST"
log "Cópia de trabalho no Termux: $OUT"
echo "$DEST" >> "$TJU_DIR/artifacts"

END_TS="$(date +%s)"
status "SUCCESS"
log "##TJU_DONE## 1 resultado em $((END_TS-START_TS))s"
cleanup
exit 0
