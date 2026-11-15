MODRINTH_PROJECT="ueQBZEGo"
RELEASE_TYPE="release"
DISPLAY_NAME="Fiw Graves"
KOTLIN_RUNTIME="none"
OPTIONAL_LUCKPERMS="false"
TARGETS=(
  "fabric-26.1.2 fabric 26.1.2"
  "fabric-1.21.11 fabric 1.21.11"
  "neoforge-1.21.11 neoforge 1.21.11"
  "fabric-1.21.8 fabric 1.21.8"
  "neoforge-1.21.8 neoforge 1.21.8"
  "fabric-1.21.1 fabric 1.21.1"
  "neoforge-1.21.1 neoforge 1.21.1"
  "fabric-1.20.1 fabric 1.20.1"
  "forge-1.20.1 forge 1.20.1"
)

jar_path() {
  local module="$1"
  echo "$1/build/libs/fiwgraves-$module-$2.jar"
}
