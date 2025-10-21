// ...existing code...
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.UUID;

public class MarcadorDePonto {

    private static final List<String> registrosEmMemoria = new ArrayList<>();
    private static final int TAMANHO_CODIGO = 6;
    private static final Path ARQUIVO_REGISTROS = Paths.get("registros.txt");
    private static final Path MACHINE_ID_FILE = Paths.get(".machine_id");
    private static final ZoneId ZONE_SP = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=========================================");
        System.out.println("==   Sistema de Marcador de Ponto      ==");
        System.out.println("==  (Registros persistidos em arquivo)  ==");
        System.out.println("=========================================");

        String machineId = getMachineId();
        if (machineId == null) {
            System.out.println("!!! ATENÇÃO: Não foi possível obter/criar o ID da máquina. Usando placeholder.");
            machineId = "ID_NAO_ENCONTRADO";
        }

        carregarRegistrosDoArquivo();

        if (!verificarIntegridadeAoIniciar()) {
            System.err.println("INTEGRIDADE DO ARQUIVO 'registros.txt' COMPROMETIDA. Saindo.");
            scanner.close();
            System.exit(1);
        }

        while (true) {
            System.out.println("\nEscolha uma opção:");
            System.out.println("1. Registrar ENTRADA");
            System.out.println("2. Registrar SAÍDA");
            System.out.println("3. Ver registros da sessão");
            System.out.println("4. Mostrar tempo trabalhado (esta máquina) por dia");
            System.out.println("5. Sair do programa");
            System.out.print(">> ");

            String escolha = scanner.nextLine();

            switch (escolha) {
                case "1":
                    processarRegistro("ENTRADA", scanner, machineId);
                    break;
                case "2":
                    processarRegistro("SAIDA", scanner, machineId);
                    break;
                case "3":
                    mostrarRegistros();
                    break;
                case "4":
                    mostrarTempoTrabalhado(machineId);
                    break;
                case "5":
                    System.out.println("Programa finalizado. Os registros estão em " + ARQUIVO_REGISTROS.toAbsolutePath());
                    scanner.close();
                    return;
                default:
                    System.out.println("Opção inválida! Por favor, tente novamente.");
                    break;
            }
        }
    }

    private static void processarRegistro(String tipoRegistro, Scanner scanner, String machineId) {
        String codigoDesafio = gerarCodigoAleatorio(TAMANHO_CODIGO);

        System.out.println("\n================ PROVA DE PRESENÇA ================");
        System.out.println("Para confirmar o registro, digite o código abaixo:");
        System.out.println("CÓDIGO: " + codigoDesafio);
        System.out.println("===================================================");
        System.out.print(">> ");

        String inputUsuario = scanner.nextLine();

        if (codigoDesafio.equals(inputUsuario)) {
            registrarPonto(tipoRegistro, codigoDesafio, machineId);
            System.out.println(">>> " + tipoRegistro + " registrada com sucesso! <<<");
        } else {
            System.out.println("!!! Código incorreto. O registro não foi efetuado. Tente novamente. !!!");
        }
    }

    private static void registrarPonto(String tipo, String prova, String machineId) {
        ZonedDateTime agoraEmSaoPaulo = ZonedDateTime.now(ZONE_SP);
        String timestamp = agoraEmSaoPaulo.format(TS_FMT);

        String linhaRegistro = String.format("TIPO: %-7s | DATA/HORA: %s | ID MÁQUINA: %s | PROVA: %s",
                tipo, timestamp, machineId, prova);

        // se for SAIDA, procura a última ENTRADA e calcula duração
        if ("SAIDA".equalsIgnoreCase(tipo)) {
            ZonedDateTime ultimaEntrada = encontrarUltimaEntrada(machineId);
            if (ultimaEntrada != null) {
                Duration dur = Duration.between(ultimaEntrada, agoraEmSaoPaulo);
                if (dur.isNegative()) dur = Duration.ZERO;
                long h = dur.toHours();
                long m = dur.toMinutesPart();
                long s = dur.toSecondsPart();
                String durStr = String.format("%02d:%02d:%02d", h, m, s);
                linhaRegistro = linhaRegistro + " | DURACAO: " + durStr;
                System.out.println("Duração desde a última entrada: " + durStr);
            } else {
                linhaRegistro = linhaRegistro + " | DURACAO: N/A";
                System.out.println("Nenhuma entrada anterior encontrada para calcular duração.");
            }
        }

        registrosEmMemoria.add(linhaRegistro);
        salvarRegistroNoArquivo(linhaRegistro);
    }

    private static String gerarCodigoAleatorio(int tamanho) {
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder codigo = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < tamanho; i++) {
            int index = random.nextInt(caracteres.length());
            codigo.append(caracteres.charAt(index));
        }
        return codigo.toString();
    }

    private static void mostrarRegistros() {
        System.out.println("\n--- Registros da Sessão Atual ---");
        if (registrosEmMemoria.isEmpty()) {
            System.out.println("Nenhum registro foi feito nesta sessão.");
        } else {
            for (String registro : registrosEmMemoria) {
                System.out.println(registro);
            }
        }
        System.out.println("---------------------------------");
    }

    // encontra a última ENTRADA (timestamp) para esta máquina
    private static ZonedDateTime encontrarUltimaEntrada(String machineId) {
        List<String> todas = new ArrayList<>(registrosEmMemoria);
        try {
            if (Files.exists(ARQUIVO_REGISTROS)) {
                List<String> fromFile = Files.readAllLines(ARQUIVO_REGISTROS);
                for (String l : fromFile) if (!todas.contains(l)) todas.add(l);
            }
        } catch (IOException ignored) {
        }

        for (int i = todas.size() - 1; i >= 0; i--) {
            String linha = todas.get(i);
            if (linha.toUpperCase().contains("TIPO:") && linha.toUpperCase().contains("ENTRADA") && linha.contains("ID MÁQUINA:")) {
                String id = extrairCampo(linha, "ID MÁQUINA:");
                if (id != null && id.equals(machineId)) {
                    String tsStr = extrairCampo(linha, "DATA/HORA:");
                    if (tsStr != null) {
                        try {
                            LocalDateTime ldt = LocalDateTime.parse(tsStr, TS_FMT);
                            return ZonedDateTime.of(ldt, ZONE_SP);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        return null;
    }

    // extrai valor entre chave e próximo '|' ou fim da linha
    private static String extrairCampo(String linha, String chave) {
        int idx = linha.indexOf(chave);
        if (idx == -1) return null;
        int start = idx + chave.length();
        int end = linha.indexOf("|", start);
        if (end == -1) end = linha.length();
        return linha.substring(start, end).trim();
    }

    private static String getMachineId() {
        // 1) Se houver ID persistido, retorna ele
        try {
            if (Files.exists(MACHINE_ID_FILE)) {
                String persisted = Files.readString(MACHINE_ID_FILE).trim();
                if (!persisted.isEmpty()) {
                    return persisted;
                }
            }
        } catch (IOException ignored) {
        }

        // 2) Tenta achar MAC de interfaces não-loopback/ativas
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    try {
                        if (ni == null || ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;
                    } catch (SocketException e) {
                        continue;
                    }

                    byte[] mac = ni.getHardwareAddress();
                    if (mac != null && mac.length > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < mac.length; i++) {
                            sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                        }
                        String macStr = sb.toString();
                        try {
                            Files.writeString(MACHINE_ID_FILE, macStr, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        } catch (IOException ignored) {
                        }
                        return macStr;
                    }
                }
            }
        } catch (SocketException ignored) {
        }

        // 3) Tenta hostname
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) {
                try {
                    Files.writeString(MACHINE_ID_FILE, host, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException ignored) {
                }
                return host;
            }
        } catch (UnknownHostException ignored) {
        }

        // 4) Por fim gera um UUID persistido
        String uuid = "UUID-" + UUID.randomUUID().toString();
        try {
            Files.writeString(MACHINE_ID_FILE, uuid, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
        }
        return uuid;
    }

    private static void carregarRegistrosDoArquivo() {
        Path path = ARQUIVO_REGISTROS;
        if (Files.exists(path)) {
            try {
                List<String> linhas = Files.readAllLines(path);
                registrosEmMemoria.addAll(linhas);
            } catch (IOException e) {
                System.err.println("Erro ao carregar registros: " + e.getMessage());
            }
        }
    }

    private static void salvarRegistroNoArquivo(String linhaRegistro) {
        try {
            Files.writeString(ARQUIVO_REGISTROS, linhaRegistro + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            atualizarHashArquivo();
        } catch (IOException e) {
            System.err.println("Erro ao salvar registro: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("Erro ao atualizar hash: " + e.getMessage());
        }
    }

    // calcula SHA-256 e grava em registros.txt.sha256
    private static void atualizarHashArquivo() {
        try {
            byte[] data = Files.readAllBytes(ARQUIVO_REGISTROS);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            String hash = bytesToHex(digest);
            Files.writeString(Paths.get(ARQUIVO_REGISTROS.toString() + ".sha256"),
                    hash, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // verifica integridade ao iniciar; retorna true se ok
    private static boolean verificarIntegridadeAoIniciar() {
        try {
            Path hashPath = Paths.get(ARQUIVO_REGISTROS.toString() + ".sha256");
            if (!Files.exists(ARQUIVO_REGISTROS) || !Files.exists(hashPath)) return true; // nada a verificar
            byte[] data = Files.readAllBytes(ARQUIVO_REGISTROS);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            String atual = bytesToHex(digest);
            String salvo = Files.readString(hashPath).trim();
            return atual.equalsIgnoreCase(salvo);
        } catch (Exception e) {
            System.err.println("Erro ao verificar integridade: " + e.getMessage());
            return false;
        }
    }

    // mostra tempo trabalhado por dia para a máquina (ENTRADA/SAIDA pareados)
    private static void mostrarTempoTrabalhado(String machineId) {
        List<String> todas = new ArrayList<>(registrosEmMemoria);
        try {
            if (Files.exists(ARQUIVO_REGISTROS)) {
                List<String> fromFile = Files.readAllLines(ARQUIVO_REGISTROS);
                for (String l : fromFile) if (!todas.contains(l)) todas.add(l);
            }
        } catch (IOException ignored) {
        }

        List<Evento> eventos = new ArrayList<>();
        for (String linha : todas) {
            String id = extrairCampo(linha, "ID MÁQUINA:");
            if (id == null || !id.equals(machineId)) continue;
            String tipo = null;
            if (linha.contains("TIPO:")) {
                int idx = linha.indexOf("TIPO:");
                int end = linha.indexOf("|", idx);
                if (end == -1) end = linha.length();
                tipo = linha.substring(idx + "TIPO:".length(), end).trim();
            }
            String tsStr = extrairCampo(linha, "DATA/HORA:");
            if (tipo == null || tsStr == null) continue;
            try {
                LocalDateTime ldt = LocalDateTime.parse(tsStr, TS_FMT);
                ZonedDateTime ts = ZonedDateTime.of(ldt, ZONE_SP);
                eventos.add(new Evento(tipo.toUpperCase(), ts));
            } catch (Exception ignored) {
            }
        }
        eventos.sort((a, b) -> a.ts.compareTo(b.ts));

        Map<LocalDate, Duration> porDia = new TreeMap<>();
        ZonedDateTime ultimaEntrada = null;

        for (Evento ev : eventos) {
            if ("ENTRADA".equalsIgnoreCase(ev.tipo)) {
                ultimaEntrada = ev.ts;
            } else if ("SAIDA".equalsIgnoreCase(ev.tipo)) {
                if (ultimaEntrada != null) {
                    distribuirDuracaoPorDia(ultimaEntrada, ev.ts, porDia);
                    ultimaEntrada = null;
                }
            }
        }

        if (ultimaEntrada != null) {
            distribuirDuracaoPorDia(ultimaEntrada, ZonedDateTime.now(ZONE_SP), porDia);
        }

        Duration total = Duration.ZERO;
        System.out.println("\n--- Relatório de Tempo Trabalhado por Dia (ID " + machineId + ") ---");
        for (Map.Entry<LocalDate, Duration> e : porDia.entrySet()) {
            Duration d = e.getValue();
            total = total.plus(d);
            long h = d.toHours();
            long m = d.toMinutesPart();
            long s = d.toSecondsPart();
            System.out.printf("%s : %02d:%02d:%02d%n", e.getKey().toString(), h, m, s);
        }
        long th = total.toHours();
        long tm = total.toMinutesPart();
        long ts = total.toSecondsPart();
        System.out.printf("TOTAL : %02d:%02d:%02d%n", th, tm, ts);
        System.out.println("--------------------------------------------------------------");
    }

    private static void distribuirDuracaoPorDia(ZonedDateTime inicio, ZonedDateTime fim, Map<LocalDate, Duration> map) {
        if (fim.isBefore(inicio)) return;
        ZonedDateTime cur = inicio;
        while (cur.isBefore(fim)) {
            ZonedDateTime nextDayStart = cur.toLocalDate().plusDays(1).atStartOfDay(ZONE_SP);
            ZonedDateTime segmentEnd = fim.isBefore(nextDayStart) ? fim : nextDayStart;
            Duration part = Duration.between(cur, segmentEnd);
            LocalDate dia = cur.toLocalDate();
            map.put(dia, map.getOrDefault(dia, Duration.ZERO).plus(part));
            cur = segmentEnd;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static class Evento {
        final String tipo;
        final ZonedDateTime ts;
        Evento(String tipo, ZonedDateTime ts) { this.tipo = tipo; this.ts = ts; }
    }
}
// ...existing code...