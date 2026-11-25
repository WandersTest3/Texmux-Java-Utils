package com.wanderstest3.termuxjavautils.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DiagnosisEngine {

    private static final String JAVA_ENV =
            "export JAVA_HOME=\"$PREFIX/lib/jvm/java-17-openjdk\"; export PATH=\"$JAVA_HOME/bin:$PATH\"; ";

    private final String sdkDir;
    private final Map<String, Diagnosis> found = new LinkedHashMap<>();

    public DiagnosisEngine(String sdkDir) {
        String d = sdkDir == null || sdkDir.trim().isEmpty() ? "$HOME/android-sdk" : sdkDir.trim();
        if (d.startsWith("~")) d = "$HOME" + d.substring(1);
        this.sdkDir = d;
    }

    public List<Diagnosis> analyze(String log, boolean failed) {
        found.clear();
        if (log == null) log = "";
        analyzeMarkers(log);
        analyzeGradle(log);
        if (failed && found.isEmpty()) {
            addGeneric(log);
        }
        List<Diagnosis> out = new ArrayList<>(found.values());
        out.sort((a, b) -> a.severity.compareTo(b.severity));
        return out;
    }

    private void put(Diagnosis d) {
        if (!found.containsKey(d.code)) found.put(d.code, d);
    }

    private static String find(String log, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(log);
        return m.find() ? (m.groupCount() >= 1 && m.group(1) != null ? m.group(1) : m.group()) : null;
    }

    private static boolean has(String log, String regex) {
        return Pattern.compile(regex, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE).matcher(log).find();
    }

    private void analyzeMarkers(String log) {
        Matcher m = Pattern.compile("^##TJU_(FAIL|WARN)## (\\S+)(?: (.*))?$", Pattern.MULTILINE).matcher(log);
        while (m.find()) {
            boolean warn = "WARN".equals(m.group(1));
            String code = m.group(2);
            String detail = m.group(3) == null ? "" : m.group(3).trim();
            markerDiagnosis(code, detail, warn);
        }
    }

    private void markerDiagnosis(String code, String detail, boolean warn) {
        switch (code) {
            case "JAVA_MISSING":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Java não está instalado no Termux",
                        "O Gradle precisa de um JDK e o app não encontrou nenhum no Termux.",
                        "Toque em \"Corrigir automaticamente\" para instalar o OpenJDK 17.",
                        "Se você já tem o JDK em outro lugar, informe o caminho em Avançado → Java home.")
                        .fix("Instalar OpenJDK 17", "pkg install -y openjdk-17"));
                break;
            case "TOOL_MISSING":
                String pkg = detail.isEmpty() ? "unzip" : detail;
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Falta o programa \"" + pkg + "\" no Termux",
                        "Esse programa é necessário para " + ("unzip".equals(pkg) ? "extrair o ZIP." : "baixar o repositório."),
                        "Toque em \"Corrigir automaticamente\" para instalar.")
                        .fix("Instalar " + pkg, "pkg install -y " + pkg));
                break;
            case "STORAGE_PERMISSION":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "O Termux não tem acesso à pasta Downloads",
                        "O Termux ainda não recebeu permissão para ler e gravar no armazenamento do celular.",
                        "Abra o Termux e execute: termux-setup-storage",
                        "Toque em \"Permitir\" na janela do Android.",
                        "Volte aqui e compile de novo.")
                        .copy("termux-setup-storage"));
                break;
            case "DISK_LOW":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Pouco espaço livre no armazenamento",
                        "Compilar com Gradle precisa de pelo menos 1,5 GB livres (dependências + build).",
                        "Libere espaço no celular.",
                        "Apague projetos antigos em ~/projects e a pasta ~/.gradle/caches se não precisar."));
                break;
            case "ZIP_NOT_FOUND":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "ZIP não encontrado: " + detail,
                        "Não existe esse arquivo na pasta de downloads configurada.",
                        "Confira o nome exato (inclusive maiúsculas e a extensão .zip).",
                        "Use o botão \"Listar ZIPs\" para escolher da lista.",
                        "No log acima aparecem os ZIPs mais recentes encontrados."));
                break;
            case "ZIP_CORRUPT":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Não consegui extrair o ZIP",
                        "O arquivo está corrompido ou o download não terminou.",
                        "Baixe o ZIP novamente.", "Teste abrindo o arquivo em um gerenciador de arquivos."));
                break;
            case "DOTNET_NO_TOKEN":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Projeto .NET precisa do token do GitHub",
                        "Esse projeto não é Gradle e só compila no GitHub Actions, que exige o token.",
                        "Salve um token com as permissões repo e workflow na tela GitHub Actions e rode o build de novo."));
                break;
            case "DOTNET_REPO_NOT_FOUND":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Repositório não encontrado no GitHub",
                        "Não achei \"" + detail + "\" com o token salvo.",
                        "Use o modo Git com a URL do repositório ou confirme que o token tem acesso a ele."));
                break;
            case "DOTNET_NO_WORKFLOW":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Nenhum workflow com disparo manual",
                        "Nenhum arquivo em .github/workflows tem workflow_dispatch.",
                        "Adicione \"workflow_dispatch:\" em on: no workflow de build e envie ao repositório."));
                break;
            case "DOTNET_DISPATCH_FAILED":
            case "DOTNET_NO_RUN":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Não consegui iniciar o workflow " + detail,
                        "O GitHub recusou ou não iniciou a execução.",
                        "Confirme que o token tem a permissão workflow e que o arquivo existe na branch no GitHub."));
                break;
            case "DOTNET_RUN_FAILED":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "O build no GitHub Actions falhou",
                        "A execução terminou com erro: " + detail,
                        "Abra o link, veja o passo que falhou e corrija o projeto."));
                break;
            case "DOTNET_DOWNLOAD_FAILED":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Falha ao baixar o artefato",
                        "O build terminou, mas o download do resultado falhou.",
                        "Tente de novo; artefatos expiram conforme a retenção do repositório."));
                break;
            case "NOT_GRADLE_PROJECT":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Isso não parece um projeto Gradle",
                        "Não achei gradlew nem settings.gradle dentro do código-fonte.",
                        "Confirme que o ZIP/repositório é o projeto completo, não só uma subpasta.",
                        "Projetos que usam Maven ou Ant não são suportados."));
                break;
            case "EXTRA_MISSING":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Arquivo extra não encontrado",
                        "O arquivo " + detail + " (assinatura/chaves) não existe.",
                        "Confira a pasta e os nomes em \"Arquivos extras\".",
                        "A pasta é lida dentro do Termux (ex.: ~/projects/MeuApp)."));
                break;
            case "KEYSTORE_INVALID":
                put(new Diagnosis(code, warn ? Diagnosis.Severity.WARNING : Diagnosis.Severity.ERROR,
                        "Não consegui validar a keystore",
                        "O keytool não abriu " + detail + " com a senha de .store_password.txt (ou o alias de .key_alias.txt). "
                                + "O build continuou; se a assinatura falhar, o motivo aparecerá abaixo.",
                        "Verifique se os arquivos de senha estão sem espaços ou linhas extras.",
                        "Teste no Termux: keytool -list -keystore " + detail));
                break;
            case "WRAPPER_JAR_MISSING":
            case "NO_GRADLEW":
                put(wrapperMissing(warn));
                break;
            case "MODULE_NOT_FOUND":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Módulo \"" + detail + "\" não existe",
                        "Não há pasta com esse nome no projeto.",
                        "Veja a lista de módulos no log acima.",
                        "Deixe o campo Módulo vazio para o app detectar sozinho."));
                break;
            case "SDK_MISSING":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Android SDK não encontrado",
                        "A pasta " + detail + " não existe no Termux.",
                        "Instale o Android SDK no Termux (platforms e build-tools).",
                        "Se ele estiver em outro lugar, ajuste Avançado → Pasta do Android SDK."));
                break;
            case "SDK_NO_PLATFORM":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "O SDK não tem nenhuma plataforma",
                        "A pasta " + detail + " está vazia.",
                        "Instale a plataforma usada pelo projeto (compileSdk), por exemplo:",
                        "sdkmanager \"platforms;android-34\" \"build-tools;34.0.0\"")
                        .fix("Instalar plataforma 34", sdkManager("\"platforms;android-34\" \"build-tools;34.0.0\"")));
                break;
            case "AAPT2_MISSING":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "aapt2 do Termux não instalado",
                        "O aapt2 que vem com o Gradle não roda no Android; o Termux fornece uma versão própria.",
                        "Toque em \"Corrigir automaticamente\" para instalar.")
                        .fix("Instalar aapt2", "pkg install -y aapt2"));
                break;
            case "OOM_KILLED":
                put(oomKilled());
                break;
            case "GRADLE_FAILED":
                break;
            case "NO_OUTPUT":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "O build terminou mas não gerou APK/JAR",
                        "A tarefa " + detail + " rodou sem produzir arquivo novo.",
                        "Troque \"Tipo de saída\" (Release/Debug/JAR).",
                        "Informe o módulo manualmente se o projeto tiver vários."));
                break;
            case "COPY_FAILED":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Não consegui copiar o resultado",
                        "Sem permissão de escrita em " + detail + ".",
                        "Rode termux-setup-storage no Termux e permita o acesso.",
                        "O arquivo compilado continua dentro da pasta do projeto no Termux.")
                        .copy("termux-setup-storage"));
                break;
            case "CLONE_FAILED":
                put(cloneFailed(detail));
                break;
            case "WORKDIR_FAILED":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Não consegui criar a pasta de trabalho",
                        "Sem permissão em " + detail + ".", "Mude a pasta em Avançado → Pasta de trabalho."));
                break;
            case "UNSIGNED_APK":
                put(new Diagnosis(code, Diagnosis.Severity.WARNING, "APK sem assinatura: " + detail,
                        "Esse APK de release não está assinado e o Android não vai instalá-lo.",
                        "Informe a keystore em \"Arquivos extras\" e configure o signingConfig no projeto,",
                        "ou use o tipo de saída Debug, que o Android instala normalmente."));
                break;
            case "PLATFORM_MISSING":
                put(new Diagnosis(code, Diagnosis.Severity.WARNING, "Plataforma " + detail + " não está no SDK",
                        "O projeto pede essa plataforma. O Gradle tentará baixar; se falhar, instale manualmente.",
                        "sdkmanager \"platforms;" + detail + "\"")
                        .fix("Instalar " + detail, sdkManager("\"platforms;" + detail + "\"")));
                break;
            case "SIGN_ZIP_NOT_FOUND":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "ZIP de assinatura não encontrado: " + detail,
                        "O arquivo informado não existe na pasta de downloads.",
                        "Confira o nome ou deixe o campo vazio para o app procurar sozinho.",
                        "O nome padrão é o do projeto + \"assinatura\", ex.: Pojarush_Test3-assinatura.zip."));
                break;
            case "SIGN_ZIP_CORRUPT":
            case "SIGN_ZIP_EMPTY":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "ZIP de assinatura inválido",
                        "Não consegui extrair o ZIP ou ele não contém um arquivo .jks/.keystore.",
                        "O ZIP deve conter a keystore (.jks) e os arquivos .store_password.txt, .key_password.txt e .key_alias.txt."));
                break;
            case "SIGN_NO_PASSWORDS":
                put(new Diagnosis(code, Diagnosis.Severity.WARNING, "Keystore sem arquivo de senha",
                        "Encontrei " + detail + ", mas não há .store_password.txt. O Gradle pode não conseguir assinar.",
                        "Inclua .store_password.txt, .key_password.txt e .key_alias.txt no ZIP de assinatura."));
                break;
            case "TERMUX_NO_RESPONSE":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "O Termux não respondeu",
                        "O comando foi enviado mas o Termux não retornou nada.",
                        "Abra o Termux uma vez e deixe-o aberto por alguns segundos.",
                        "No Termux, ative a opção: allow-external-apps = true (veja o passo a passo em Preparar Termux).",
                        "Desative a otimização de bateria do Termux.",
                        "Confirme que o Termux é da F-Droid/GitHub (a versão da Play Store não funciona).")
                        .copy("mkdir -p ~/.termux && sed -i '/allow-external-apps/d' ~/.termux/termux.properties 2>/dev/null; echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings"));
                break;
            case "TERMUX_ERROR":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "O Termux recusou o comando",
                        detail.isEmpty() ? "O Termux retornou um erro." : detail,
                        "Ative allow-external-apps = true em ~/.termux/termux.properties.",
                        "Depois execute: termux-reload-settings",
                        "Confira a permissão \"Executar comandos no Termux\" em Preparar Termux.")
                        .copy("mkdir -p ~/.termux && sed -i '/allow-external-apps/d' ~/.termux/termux.properties 2>/dev/null; echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings"));
                break;
            case "TERMUX_PERMISSION":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Falta a permissão para executar comandos no Termux",
                        "O Android ainda não concedeu a este app a permissão do Termux.",
                        "Abra Preparar Termux e toque em \"Conceder permissão\".",
                        "Se não aparecer a janela, abra Configurações do Android → Apps → Termux Java Utils → Permissões."));
                break;
            case "TERMUX_LOST":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Perdi a comunicação com o Termux",
                        "O build pode ter continuado dentro do Termux, mas o app parou de receber o andamento.",
                        "Abra o Termux e veja se o build ainda roda.",
                        "Toque em \"Retomar último build\" para tentar reconectar.",
                        "Desative a otimização de bateria do Termux e deste app."));
                break;
            case "DEC_INPUT_NOT_FOUND":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Arquivo não encontrado: " + detail,
                        "O Termux não achou esse arquivo.",
                        "Use o botão \"Escolher arquivo\" para selecioná-lo na memória do celular.",
                        "Se digitou o nome, confira maiúsculas e a extensão.",
                        "No log acima aparecem os arquivos mais recentes de Downloads."));
                break;
            case "DEC_INPUT_CORRUPT":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "O arquivo parece corrompido: " + detail,
                        "Não foi possível abrir o conteúdo como ZIP/APK/JAR.",
                        "Baixe ou copie o arquivo de novo.",
                        "APKs divididos (.apks/.xapk) precisam ser extraídos antes: descompile o APK base."));
                break;
            case "DEC_UNSUPPORTED":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Formato não suportado: ." + detail,
                        "O app descompila APK, JAR, AAR, DEX, CLASS e ZIP.",
                        "Escolha um arquivo com uma dessas extensões."));
                break;
            case "DEC_MODE_UNSUPPORTED":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Essa opção não funciona com arquivos ." + detail,
                        "A conversão para JAR (dex2jar) só aceita APK e DEX.",
                        "Escolha \"Código Java (jadx)\" para JAR, AAR e ZIP."));
                break;
            case "DEC_APK_ONLY":
                put(new Diagnosis(code, Diagnosis.Severity.WARNING, "Smali só existe para APK",
                        "O arquivo é ." + detail + ", então o app gerou apenas o Java com o jadx.",
                        "Para obter Smali, use o APK do app."));
                break;
            case "DEC_PARTIAL":
                put(new Diagnosis(code, Diagnosis.Severity.WARNING, "Descompilação parcial",
                        detail.isEmpty() ? "Algumas classes não puderam ser convertidas." : detail,
                        "É normal em apps ofuscados: os métodos com problema aparecem comentados no código gerado.",
                        "Tente ativar \"Desofuscar nomes\" ou usar Smali para ver essas partes."));
                break;
            case "DEC_TOOL_FAILED":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "O " + detail + " não conseguiu processar o arquivo",
                        "A ferramenta terminou sem gerar nenhum arquivo.",
                        "Abra o log completo: ele mostra o erro do " + detail + ".",
                        "Se o arquivo for ofuscado ou protegido, tente outro modo (Smali ou Só extrair)."));
                break;
            case "TOOL_INSTALL_FAILED":
                put(new Diagnosis(code, Diagnosis.Severity.ERROR, "Não consegui instalar o pacote \"" + detail + "\"",
                        "O pkg do Termux falhou (sem internet, espelho fora do ar ou lista desatualizada).",
                        "Verifique a conexão e tente \"Corrigir automaticamente\".")
                        .fix("Atualizar e instalar " + detail, "pkg update -y && pkg install -y " + detail));
                break;
            case "CANCELLED":
                put(new Diagnosis(code, Diagnosis.Severity.WARNING, "Build cancelado",
                        "Você cancelou o build.", "Toque em Compilar para tentar de novo."));
                break;
            case "CMD_FAILED":
                break;
            default:
                break;
        }
    }

    private Diagnosis wrapperMissing(boolean warn) {
        return new Diagnosis(warn ? "WRAPPER_JAR_MISSING_W" : "WRAPPER_JAR_MISSING",
                warn ? Diagnosis.Severity.WARNING : Diagnosis.Severity.ERROR,
                "O projeto não tem o Gradle Wrapper completo",
                "Faltam gradlew ou gradle/wrapper/gradle-wrapper.jar (geralmente ignorados no ZIP/repositório).",
                warn ? "Usei o Gradle instalado no Termux no lugar do wrapper."
                        : "Instale o Gradle no Termux; o app o usará automaticamente.",
                "Ou adicione o wrapper ao projeto (gradle wrapper) e envie ao repositório.")
                .fix("Instalar Gradle", "pkg install -y gradle");
    }

    private Diagnosis oomKilled() {
        return new Diagnosis("OOM_KILLED", Diagnosis.Severity.ERROR, "O Android encerrou o processo (pouca memória)",
                "O processo foi morto (código 137): falta de RAM ou o Android 12+ matou processos filhos do Termux.",
                "Feche outros apps e tente de novo.",
                "Em Opções do desenvolvedor, ative \"Desativar restrições de processos filhos\" (Android 12+).",
                "Ao compilar, em Argumentos extras do Gradle use: -Dorg.gradle.jvmargs=-Xmx1024m --max-workers=1",
                "Desative a otimização de bateria do Termux e deste app.");
    }

    private Diagnosis cloneFailed(String url) {
        return new Diagnosis("CLONE_FAILED", Diagnosis.Severity.ERROR, "Não consegui baixar o repositório",
                "O download de " + url + " falhou.",
                "Confira o link e o nome da branch.",
                "Repositório privado: salve o token em GitHub Actions e marque \"Usar token salvo\".",
                "Veja o log: ele mostra o motivo (autenticação, branch inexistente ou sem internet).");
    }

    private String sdkManager(String pkgs) {
        return JAVA_ENV + "SDK=\"" + sdkDir + "\"; yes | \"$SDK/cmdline-tools/latest/bin/sdkmanager\" --sdk_root=\"$SDK\" " + pkgs;
    }

    private void analyzeGradle(String log) {
        String v;

        if (has(log, "Could not find or load main class org\\.gradle\\.wrapper\\.GradleWrapperMain")) {
            put(wrapperMissing(false));
        }
        if (has(log, "bad interpreter: No such file or directory|/usr/bin/env: .*No such file")) {
            put(new Diagnosis("SHEBANG", Diagnosis.Severity.ERROR, "Script com interpretador inexistente no Termux",
                    "Um script do projeto começa com #!/usr/bin/env, que não existe no Termux.",
                    "Para o gradlew o app já usa \"sh ./gradlew\". Se for outro script, rode no Termux:",
                    "termux-fix-shebang nome-do-script")
                    .copy("termux-fix-shebang gradlew"));
        }
        if (has(log, "SDK location not found")) {
            put(new Diagnosis("SDK_LOCATION", Diagnosis.Severity.ERROR, "O Gradle não achou o Android SDK",
                    "Nem ANDROID_HOME nem local.properties apontam para um SDK.",
                    "Defina a pasta correta em Avançado → Pasta do Android SDK."));
        }
        if (has(log, "(AAPT2 .*Daemon startup failed|aapt2.*(Exec format error|cannot execute binary|Syntax error)|Android resource linking failed.*aapt2)")) {
            put(new Diagnosis("AAPT2_ARM", Diagnosis.Severity.ERROR, "O aapt2 do Gradle não roda neste celular",
                    "O aapt2 baixado pelo Gradle é para PC. No Termux é preciso usar o aapt2 do próprio Termux.",
                    "Ative a opção \"Usar aapt2 do Termux\" e compile de novo.",
                    "Instale o aapt2 se ainda não tiver.")
                    .fix("Instalar aapt2", "pkg install -y aapt2"));
        }
        v = find(log, "Android Gradle plugin requires Java (\\d+) to run\\. You are currently using Java (\\d+)");
        if (v != null) {
            Matcher m = Pattern.compile("requires Java (\\d+).*using Java (\\d+)", Pattern.DOTALL).matcher(log);
            String need = "17", cur = "?";
            if (m.find()) { need = m.group(1); cur = m.group(2); }
            put(javaVersion(need, cur));
        }
        v = find(log, "Unsupported class file major version (\\d+)");
        if (v != null) {
            put(new Diagnosis("JAVA_TOO_NEW", Diagnosis.Severity.ERROR, "Versão do Java incompatível com este Gradle",
                    "O Gradle do projeto é antigo demais para o Java em uso (class file " + v + ").",
                    "Use um JDK mais antigo (17) em Avançado → Java home,",
                    "ou atualize o Gradle do projeto em gradle/wrapper/gradle-wrapper.properties."));
        }
        v = find(log, "(?:invalid source release|release version|invalid target release): (\\d+)");
        if (v != null) {
            put(javaVersion(v, "?"));
        }
        v = find(log, "Minimum supported Gradle version is ([\\d.]+)\\. Current version is ([\\d.]+)");
        if (v != null) {
            put(new Diagnosis("GRADLE_OLD", Diagnosis.Severity.ERROR, "Versão do Gradle antiga para este projeto",
                    "O plugin exige Gradle " + v + " ou superior.",
                    "Edite distributionUrl em gradle/wrapper/gradle-wrapper.properties para a versão exigida."));
        }
        if (has(log, "(licenses have not been accepted|License for package .* not accepted|You have not accepted the license agreements)")) {
            put(new Diagnosis("LICENSES", Diagnosis.Severity.ERROR, "Licenças do Android SDK não aceitas",
                    "O Gradle precisa baixar componentes do SDK mas as licenças ainda não foram aceitas.",
                    "Toque em \"Corrigir automaticamente\" para aceitar todas as licenças.",
                    "Requer cmdline-tools instalado no SDK.")
                    .fix("Aceitar licenças", JAVA_ENV + "SDK=\"" + sdkDir + "\"; yes | \"$SDK/cmdline-tools/latest/bin/sdkmanager\" --sdk_root=\"$SDK\" --licenses"));
        }
        v = find(log, "(?:Failed to find target with hash string 'android-(\\d+)'|Failed to find Platform SDK with path: platform;android-(\\d+))");
        if (v != null) {
            put(new Diagnosis("PLATFORM_FAIL", Diagnosis.Severity.ERROR, "Plataforma android-" + v + " ausente no SDK",
                    "O projeto compila contra a API " + v + ", que não está instalada.",
                    "Toque em \"Corrigir automaticamente\" para instalar.")
                    .fix("Instalar android-" + v, sdkManager("\"platforms;android-" + v + "\"")));
        }
        v = find(log, "Failed to find Build Tools revision ([\\d.]+)");
        if (v != null) {
            put(new Diagnosis("BUILDTOOLS_FAIL", Diagnosis.Severity.ERROR, "Build-tools " + v + " ausente no SDK",
                    "O projeto exige a revisão " + v + " das build-tools.",
                    "Toque em \"Corrigir automaticamente\" para instalar.")
                    .fix("Instalar build-tools " + v, sdkManager("\"build-tools;" + v + "\"")));
        }
        if (has(log, "(java\\.lang\\.OutOfMemoryError|Java heap space|GC overhead limit|Metaspace|JVM heap space is exhausted)")) {
            put(new Diagnosis("OOM_JAVA", Diagnosis.Severity.ERROR, "O Gradle ficou sem memória",
                    "A JVM esgotou a memória durante o build.",
                    "Feche outros apps.",
                    "Em Argumentos extras do Gradle use: -Dorg.gradle.jvmargs=-Xmx1536m --max-workers=2",
                    "Se continuar, tente -Xmx1024m --max-workers=1."));
        }
        if (has(log, "(UnknownHostException|Could not GET|Connect to .* failed|Read timed out|Connection (reset|timed out)|SSLHandshakeException|Network is unreachable|Could not resolve host)")) {
            put(new Diagnosis("NETWORK", Diagnosis.Severity.ERROR, "Problema de internet ao baixar dependências",
                    "O Gradle ou o git não conseguiu acessar a rede.",
                    "Confira o Wi-Fi/dados móveis.",
                    "Desative VPN/DNS privado se estiver ativo e tente de novo."));
        }
        v = find(log, "Could not find ([A-Za-z0-9_.\\-]+:[A-Za-z0-9_.\\-]+:[^\\s.]+[^\\s]*)\\.");
        if (v != null) {
            put(new Diagnosis("DEP_NOT_FOUND", Diagnosis.Severity.ERROR, "Dependência não encontrada: " + v,
                    "Nenhum repositório configurado no projeto contém esse artefato.",
                    "Confira o nome/versão no build.gradle.",
                    "Pode faltar um repositório (maven { url ... }) em repositories {}."));
        }
        v = find(log, "Plugin \\[id: '([^']+)'[^\\]]*\\] was not found");
        if (v != null) {
            put(new Diagnosis("PLUGIN_NOT_FOUND", Diagnosis.Severity.ERROR, "Plugin Gradle não encontrado: " + v,
                    "O plugin não está em nenhum repositório de pluginManagement.",
                    "Confira o repositório em settings.gradle (pluginManagement { repositories { ... } }).",
                    "Mods Fabric/Forge precisam do repositório oficial do loader."));
        }
        if (has(log, "(Keystore was tampered with, or password was incorrect|Cannot recover key|Failed to read key .* from store|keystore password was incorrect)")) {
            put(new Diagnosis("SIGN_PASSWORD", Diagnosis.Severity.ERROR, "Senha da keystore incorreta",
                    "A senha da keystore ou da chave não confere.",
                    "Confira .store_password.txt e .key_password.txt (sem espaço/linha extra no fim).",
                    "Confira o alias em .key_alias.txt."));
        }
        v = find(log, "Keystore file '([^']+)' not found");
        if (v != null) {
            put(new Diagnosis("SIGN_FILE", Diagnosis.Severity.ERROR, "Keystore não encontrada pelo Gradle",
                    "O signingConfig aponta para " + v + ", que não existe.",
                    "Adicione a keystore em \"Arquivos extras\" com o mesmo nome do build.gradle.",
                    "Confira o caminho no signingConfigs do projeto."));
        }
        v = find(log, "Task '([^']+)' not found in (?:root project|project)");
        if (v != null) {
            put(new Diagnosis("TASK_NOT_FOUND", Diagnosis.Severity.ERROR, "Tarefa \"" + v + "\" não existe",
                    "O módulo escolhido não possui essa tarefa.",
                    "Troque o tipo de saída ou o módulo.",
                    "Projeto de mod Java usa a tarefa build (tipo JAR)."));
        }
        if (has(log, "Timeout waiting to lock|Could not create service of type|Another build is in progress|Could not lock")) {
            put(new Diagnosis("GRADLE_LOCK", Diagnosis.Severity.ERROR, "Outro Gradle está usando os mesmos arquivos",
                    "Há outro build rodando ou um processo preso.",
                    "Aguarde o outro build ou rode no Termux: pkill -f gradle",
                    "Depois compile de novo.").copy("pkill -f gradle"));
        }
        if (has(log, "No space left on device")) {
            put(new Diagnosis("NO_SPACE", Diagnosis.Severity.ERROR, "Armazenamento cheio",
                    "O celular ficou sem espaço durante o build.", "Libere espaço e tente de novo."));
        }
        if (has(log, "Execution failed for task ':[^']*lintVital")) {
            put(new Diagnosis("LINT", Diagnosis.Severity.ERROR, "O Lint bloqueou o build de release",
                    "O Lint encontrou erros fatais.",
                    "Para ignorar, use em Argumentos extras: -x lintVitalRelease -x lintVitalAnalyzeRelease"));
        }
        if (has(log, "Remote branch .* not found|Repository not found|Authentication failed|could not read Username|terminal prompts disabled")) {
            put(new Diagnosis("GIT_ACCESS", Diagnosis.Severity.ERROR, "Repositório inacessível",
                    "O link está errado, a branch não existe ou o repositório é privado.",
                    "Confira o link e a branch.",
                    "Privado: salve o token na tela GitHub Actions e marque \"Usar token salvo\"."));
        }
        if (has(log, "Unable to locate package ")) {
            put(new Diagnosis("PKG_LIST", Diagnosis.Severity.ERROR, "O Termux não achou o pacote",
                    "A lista de pacotes do Termux está desatualizada.",
                    "Toque em \"Corrigir automaticamente\" e depois instale de novo.")
                    .fix("Atualizar pacotes", "pkg update -y"));
        }
        compileErrors(log);
        resourceErrors(log);
    }

    private Diagnosis javaVersion(String need, String cur) {
        return new Diagnosis("JAVA_VERSION", Diagnosis.Severity.ERROR, "Precisa de Java " + need + " (em uso: " + cur + ")",
                "O projeto/plugin exige JDK " + need + " ou superior.",
                "Instale o JDK correto e informe o caminho em Avançado → Java home.",
                "Ex.: pkg install openjdk-" + need)
                .fix("Instalar OpenJDK " + need, "pkg install -y openjdk-" + need);
    }

    private void compileErrors(String log) {
        Matcher m = Pattern.compile("^(?:e: )?(?:file://)?(\\S+\\.(?:java|kt)):\\(?(\\d+)(?::\\d+)?\\)?:? (?:error: )?(.+)$", Pattern.MULTILINE).matcher(log);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        while (m.find() && n < 5) {
            String line = m.group();
            if (!line.contains("error") && !line.startsWith("e: ")) continue;
            sb.append(shortPath(m.group(1))).append(':').append(m.group(2)).append(" — ").append(m.group(3).trim()).append('\n');
            n++;
        }
        Matcher k = Pattern.compile("^e: (?:file://)?(\\S+\\.kt):? ?\\(?(\\d+)[:,] ?\\d+\\)?:? (.+)$", Pattern.MULTILINE).matcher(log);
        while (k.find() && n < 5) {
            sb.append(shortPath(k.group(1))).append(':').append(k.group(2)).append(" — ").append(k.group(3).trim()).append('\n');
            n++;
        }
        if (n > 0) {
            Diagnosis d = new Diagnosis("COMPILE_ERROR", Diagnosis.Severity.ERROR, "Erro de compilação no código-fonte",
                    "O código tem erros e não compila (não é problema do celular):\n" + sb.toString().trim(),
                    "Corrija o código nos arquivos indicados.",
                    "Pode faltar dependência, versão errada de Java ou a branch estar quebrada.");
            put(d);
        }
    }

    private void resourceErrors(String log) {
        String v = find(log, "(?:AAPT: error:|error: resource|error: attribute) (.+)");
        if (v != null && has(log, "(Android resource linking failed|processDebugResources|processReleaseResources|mergeResources)")
                && !found.containsKey("AAPT2_ARM")) {
            put(new Diagnosis("RESOURCES", Diagnosis.Severity.ERROR, "Erro em recursos do app (res/)",
                    "O aapt2 recusou um recurso: " + v.trim(),
                    "Confira o arquivo XML/imagem citado no log.",
                    "Versões de AndroidX/Material incompatíveis também causam isso."));
        }
    }

    private void addGeneric(String log) {
        String wrong = find(log, "\\* What went wrong:\\s*\\n([\\s\\S]*?)(?:\\n\\* |\\n\\z|$)");
        String detail = wrong == null ? lastErrorLines(log) : wrong.trim();
        if (detail.length() > 600) detail = detail.substring(0, 600) + "…";
        put(new Diagnosis("UNKNOWN", Diagnosis.Severity.ERROR, "O build falhou",
                detail.isEmpty() ? "Não encontrei uma causa conhecida no log." : detail,
                "Copie o log completo e procure a primeira linha com \"error\" ou \"FAILED\".",
                "Se o problema persistir, envie o log para análise."));
    }

    private static String lastErrorLines(String log) {
        String[] lines = log.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int i = lines.length - 1; i >= 0 && n < 4; i--) {
            String l = lines[i].trim();
            if (l.isEmpty() || l.startsWith("##TJU")) continue;
            if (l.toLowerCase().contains("error") || l.contains("FAILED") || l.contains("Exception")) {
                sb.insert(0, l + "\n");
                n++;
            }
        }
        return sb.toString().trim();
    }

    private static String shortPath(String p) {
        int i = p.lastIndexOf("/src/");
        return i >= 0 ? p.substring(i + 1) : p.substring(p.lastIndexOf('/') + 1);
    }
}
