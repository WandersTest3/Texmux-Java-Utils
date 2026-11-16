# Termux Java Utils

App Android para compilação de APKs e mods JAR com três abordagens.

## Abordagens

### 1. Termux Wrapper
Dispara comandos Gradle diretamente no Termux via Intent `com.termux.RUN_COMMAND`.  
Requer: Termux instalado com a permissão `RUN_COMMAND` ativada.

```
# Ativar permissão no Termux:
termux-setup-storage
```

### 2. GitHub Actions CI/CD
Dispara workflows remotos via API do GitHub e verifica o status da execução.  
Requer: Personal Access Token com permissão `repo` e `workflow`.

### 3. Gerador de Scripts
Gera scripts de build reutilizáveis para APK release, JAR de mod ou personalizado.  
Permite copiar ou enviar diretamente ao Termux.

## Build

```bash
./gradlew assembleDebug
```

Requer: JDK 17, Android SDK (compileSdk 34).

## Notas

Não adicione `androidx.lifecycle:*` 2.8.x como dependência direta: sem o plugin Kotlin, o Gradle resolve a variante JVM (`-desktop`) e o `ReportFragment` fica fora do APK, causando `NoClassDefFoundError` em `androidx.startup.InitializationProvider`.

## Licença

MIT — veja [LICENSE](LICENSE).
