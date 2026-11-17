# Termux Java Utils

App Android para compilar apps Android (APK) e mods Java (JAR) direto do celular, usando o Termux, sem precisar de PC.

## Compilar projeto (principal)

1. Escolha a origem do código:
   - **ZIP em Downloads**: digite o nome do arquivo (ou use *Listar ZIPs*).
   - **Link do repositório**: cole o link do GitHub; o app baixa o código sozinho. Repositórios privados usam o token salvo em *GitHub Actions*.
2. Escolha o que gerar (Automático, APK Release, APK Debug ou JAR) e, se precisar, o módulo e os arquivos de assinatura.
3. Toque em **Compilar agora**. O build roda em segundo plano dentro do Termux, mostra etapas e log ao vivo e avisa por notificação quando termina. O APK/JAR é copiado para a pasta Downloads.

O app detecta sozinho a raiz do projeto, o módulo Android (`com.android.application`), a tarefa Gradle e se é um app ou um mod.

### Escolher arquivos
Em vez de digitar nomes, use os botões **Escolher ZIP no celular** e **Escolher ZIP de assinatura no celular**. Arquivos que já estão na memória interna são usados no lugar; os de outros apps (Drive, etc.) são copiados para `Downloads/TermuxJavaUtils`, de onde o Termux os lê. Também dá para compartilhar ou abrir um APK/JAR/ZIP com o Termux Java Utils em qualquer gerenciador de arquivos.

### Quando falha
O app lê o log e explica o motivo com passos de correção, e oferece correção automática quando possível. Exemplos reconhecidos: Java ausente ou versão errada, `unzip`/`git` faltando, sem permissão de Downloads, ZIP não encontrado ou corrompido, Gradle Wrapper ausente, SDK/plataforma/build-tools ausentes, licenças não aceitas, aapt2 incompatível, falta de memória, rede, dependência/plugin não encontrado, senha de keystore errada e erros de compilação (arquivo:linha).

### Assinatura automática
Coloque em Downloads um ZIP com o nome do projeto + `assinatura` (ex.: `Pojarush_Test3-assinatura.zip`) contendo a keystore (`.jks`/`.keystore`) e os arquivos `.store_password.txt`, `.key_password.txt` e `.key_alias.txt`. Ao compilar, o app encontra o ZIP sozinho (ou use o campo *ZIP de assinatura*), copia os arquivos para o projeto e guarda as chaves em `~/.tju/keys/<projeto>` no Termux; nos próximos builds, inclusive por link de repositório, elas são reutilizadas sem o ZIP. Com keystore presente, o modo Automático gera Release.

O `gradlew` é executado com `sh ./gradlew`, então não depende de `/usr/bin/env` (inexistente no Termux).

### Perfis
Salve as configurações de um projeto para compilar com um toque. Há um perfil de exemplo (`Pojarush_Test3`) que você pode editar ou excluir.

## Descompilar
Escolha um APK, JAR (inclusive mods), AAR, DEX ou ZIP e o app roda no Termux, em segundo plano:
- **Java (jadx)** para qualquer formato; **Smali + recursos (apktool)** e **Java + Smali** só para APK;
- **Converter para JAR (dex2jar)** para APK/DEX; **Só extrair** o conteúdo.
O app instala o que faltar (`pkg install jadx apktool dex2jar zip`) e salva `<nome>-decompilado.zip` (ou uma pasta) em Downloads, com uma cópia em `~/decompiled`. Use apenas em arquivos que você tem direito de analisar.

## Preparar Termux (uma vez)

1. Instale o Termux pela F-Droid ou pelo GitHub.
2. Cole no Termux o comando de configuração copiado pelo app (ativa `allow-external-apps`).
3. Conceda a permissão *Executar comandos no Termux* ao app.
4. Toque em **Verificar ambiente** e depois em **Instalar o que falta**.

O Android SDK (pasta com `platforms` e `build-tools`, padrão `$HOME/android-sdk`) precisa existir no Termux para compilar apps Android.

## Outras telas

- **GitHub Actions**: dispara um workflow, mostra o status e baixa o artefato para Downloads.
- **Comandos no Termux**: roda um comando Gradle com a sessão visível.
- **Gerador de scripts**: gera scripts de build para copiar ou enviar ao Termux.

## Build do app

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Requer JDK 17 e Android SDK (compileSdk 34).

## Notas

Não adicione `androidx.lifecycle:*` 2.8.x como dependência direta: sem o plugin Kotlin, o Gradle resolve a variante JVM (`-desktop`) e o `ReportFragment` fica fora do APK, causando `NoClassDefFoundError` em `androidx.startup.InitializationProvider`.

## Licença

MIT — veja [LICENSE](LICENSE).
