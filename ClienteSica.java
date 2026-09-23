import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;

/**
 * Cliente em modo texto do SiCA.
 *
 * Depois de conectar ao servidor, o usuario pode enviar um arquivo local,
 * listar os arquivos remotos, baixar um arquivo ou encerrar a aplicacao.
 */
public class ClienteSica {
    private static final String HOST_PADRAO = "localhost";
    private static final int PORTA = 5000;
    private static final int TAMANHO_BUFFER = 8192;
    private static final Path PASTA_DOWNLOADS = Paths.get("downloads_cliente");

    /**
     * Abre a conexao TCP e mantem o menu ativo ate o usuario escolher sair.
     *
     * E possivel informar outro endereco IP como primeiro argumento. Sem
     * argumento, "localhost" e usado, ideal quando cliente e servidor rodam
     * no mesmo computador.
     */
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : HOST_PADRAO;

        try {
            Files.createDirectories(PASTA_DOWNLOADS);

            try (Socket socket = new Socket(host, PORTA);
                 DataInputStream entrada = new DataInputStream(
                         new BufferedInputStream(socket.getInputStream()));
                 DataOutputStream saida = new DataOutputStream(
                         new BufferedOutputStream(socket.getOutputStream()));
                 Scanner teclado = new Scanner(System.in)) {

                System.out.println("Conectado ao servidor SiCA em "
                        + host + ":" + PORTA + ".");

                boolean executando = true;
                while (executando) {
                    exibirMenu();
                    String opcao = teclado.nextLine().trim();

                    switch (opcao) {
                        case "1":
                            enviarArquivo(teclado, entrada, saida);
                            break;
                        case "2":
                            listarArquivos(entrada, saida);
                            break;
                        case "3":
                            baixarArquivo(teclado, entrada, saida);
                            break;
                        case "0":
                            saida.writeUTF(Protocolo.SAIR);
                            saida.flush();
                            executando = false;
                            System.out.println("Conexao encerrada.");
                            break;
                        default:
                            System.out.println("Opcao invalida. Tente novamente.");
                    }
                }
            }
        } catch (IOException erro) {
            System.err.println("Erro de comunicacao: " + erro.getMessage());
            System.err.println("Verifique se o servidor esta em execucao e se o host esta correto.");
        }
    }

    /** Exibe as operacoes disponiveis para o usuario. */
    private static void exibirMenu() {
        System.out.println();
        System.out.println("===== SiCA - Cliente =====");
        System.out.println("1 - Enviar arquivo");
        System.out.println("2 - Listar arquivos do servidor");
        System.out.println("3 - Baixar arquivo");
        System.out.println("0 - Sair");
        System.out.print("Escolha uma opcao: ");
    }

    /**
     * Valida o caminho local e envia o comando, o nome, o tamanho e os bytes
     * do arquivo. O tamanho permite que o servidor saiba onde o arquivo termina
     * sem precisar fechar a conexao TCP.
     */
    private static void enviarArquivo(Scanner teclado,
                                      DataInputStream entrada,
                                      DataOutputStream saida) throws IOException {
        System.out.print("Caminho do arquivo que sera enviado: ");
        String textoCaminho = removerAspasExternas(teclado.nextLine().trim());
        Path arquivo = Paths.get(textoCaminho).toAbsolutePath().normalize();

        if (!Files.isRegularFile(arquivo)) {
            System.out.println("Arquivo local nao encontrado.");
            return;
        }

        long tamanho = Files.size(arquivo);
        saida.writeUTF(Protocolo.ENVIAR);
        saida.writeUTF(arquivo.getFileName().toString());
        saida.writeLong(tamanho);

        try (BufferedInputStream dadosArquivo = new BufferedInputStream(
                Files.newInputStream(arquivo))) {
            byte[] buffer = new byte[TAMANHO_BUFFER];
            int quantidadeLida;
            while ((quantidadeLida = dadosArquivo.read(buffer)) != -1) {
                saida.write(buffer, 0, quantidadeLida);
            }
        }
        saida.flush();

        boolean sucesso = entrada.readBoolean();
        String mensagem = entrada.readUTF();
        System.out.println((sucesso ? "SUCESSO: " : "ERRO: ") + mensagem);
    }

    /**
     * Solicita a listagem e le primeiro a quantidade de registros. Depois, le
     * o par nome/tamanho de cada arquivo na mesma ordem usada pelo servidor.
     */
    private static void listarArquivos(DataInputStream entrada,
                                       DataOutputStream saida) throws IOException {
        saida.writeUTF(Protocolo.LISTAR);
        saida.flush();

        int quantidade = entrada.readInt();
        if (quantidade == 0) {
            System.out.println("O servidor ainda nao possui arquivos.");
            return;
        }

        System.out.println();
        System.out.println("Arquivos disponiveis:");
        for (int indice = 1; indice <= quantidade; indice++) {
            String nome = entrada.readUTF();
            long tamanho = entrada.readLong();
            System.out.printf("%d - %s (%s)%n",
                    indice, nome, formatarTamanho(tamanho));
        }
    }

    /**
     * Pede um arquivo pelo nome e grava os bytes recebidos na pasta
     * downloads_cliente. Se ja existir um arquivo com o mesmo nome, ele sera
     * substituido apenas depois que o servidor confirmar que o remoto existe.
     */
    private static void baixarArquivo(Scanner teclado,
                                      DataInputStream entrada,
                                      DataOutputStream saida) throws IOException {
        System.out.print("Nome exato do arquivo que deseja baixar: ");
        String nome = teclado.nextLine().trim();

        saida.writeUTF(Protocolo.BAIXAR);
        saida.writeUTF(nome);
        saida.flush();

        boolean encontrado = entrada.readBoolean();
        if (!encontrado) {
            System.out.println("ERRO: " + entrada.readUTF());
            return;
        }

        long tamanho = entrada.readLong();
        Path destino = PASTA_DOWNLOADS.resolve(Paths.get(nome).getFileName())
                .toAbsolutePath().normalize();

        try (BufferedOutputStream arquivo = new BufferedOutputStream(
                Files.newOutputStream(destino, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE))) {
            copiarQuantidadeExata(entrada, arquivo, tamanho);
        }

        System.out.println("SUCESSO: arquivo salvo em " + destino);
    }

    /**
     * Copia exatamente a quantidade anunciada pelo servidor. Isso e importante
     * porque uma unica chamada a read() nao garante receber o arquivo inteiro.
     */
    private static void copiarQuantidadeExata(DataInputStream entrada,
                                              BufferedOutputStream arquivo,
                                              long quantidade) throws IOException {
        byte[] buffer = new byte[TAMANHO_BUFFER];
        long restante = quantidade;

        while (restante > 0) {
            int limite = (int) Math.min(buffer.length, restante);
            int lidos = entrada.read(buffer, 0, limite);
            if (lidos == -1) {
                throw new EOFException("Transferencia encerrada antes do fim do arquivo.");
            }
            arquivo.write(buffer, 0, lidos);
            restante -= lidos;
        }
    }

    /** Permite colar no terminal caminhos escritos entre aspas. */
    private static String removerAspasExternas(String texto) {
        if (texto.length() >= 2) {
            boolean aspasDuplas = texto.startsWith("\"") && texto.endsWith("\"");
            boolean aspasSimples = texto.startsWith("'") && texto.endsWith("'");
            if (aspasDuplas || aspasSimples) {
                return texto.substring(1, texto.length() - 1);
            }
        }
        return texto;
    }

    /** Converte bytes para uma forma mais legivel no menu. */
    private static String formatarTamanho(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
