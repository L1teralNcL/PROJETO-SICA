import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Servidor do SiCA (Sistema de Compartilhamento de Arquivos).
 *
 * O servidor escuta conexoes TCP na porta 5000. Para cada cliente conectado,
 * uma tarefa separada interpreta os comandos ENVIAR, LISTAR, BAIXAR e SAIR.
 */
public class ServidorSica {
    private static final int PORTA = 5000;
    private static final int TAMANHO_BUFFER = 8192;
    private static final Path PASTA_SERVIDOR = Paths.get("arquivos_servidor");

    /**
     * Inicia o servidor e permanece aguardando clientes.
     *
     * A pasta de armazenamento e criada automaticamente. O pool de threads
     * permite atender varios clientes ao mesmo tempo sem que um bloqueie outro.
     */
    public static void main(String[] args) {
        ExecutorService poolDeClientes = Executors.newCachedThreadPool();

        try {
            Files.createDirectories(PASTA_SERVIDOR);

            try (ServerSocket servidor = new ServerSocket(PORTA)) {
                System.out.println("Servidor SiCA iniciado na porta " + PORTA + ".");
                System.out.println("Pasta compartilhada: "
                        + PASTA_SERVIDOR.toAbsolutePath().normalize());

                while (true) {
                    Socket cliente = servidor.accept();
                    System.out.println("Cliente conectado: "
                            + cliente.getRemoteSocketAddress());
                    poolDeClientes.submit(() -> atenderCliente(cliente));
                }
            }
        } catch (IOException erro) {
            System.err.println("Nao foi possivel iniciar o servidor: "
                    + erro.getMessage());
        } finally {
            poolDeClientes.shutdown();
        }
    }

    /**
     * Le e processa repetidamente os comandos enviados por um cliente.
     *
     * DataInputStream e DataOutputStream sao usados para que textos, numeros,
     * valores booleanos e bytes sejam enviados na mesma ordem e interpretados
     * corretamente nas duas pontas da conexao.
     *
     * @param socket conexao TCP estabelecida com o cliente
     */
    private static void atenderCliente(Socket socket) {
        try (Socket cliente = socket;
             DataInputStream entrada = new DataInputStream(
                     new BufferedInputStream(cliente.getInputStream()));
             DataOutputStream saida = new DataOutputStream(
                     new BufferedOutputStream(cliente.getOutputStream()))) {

            boolean conectado = true;
            while (conectado) {
                String comando = entrada.readUTF();

                switch (comando) {
                    case Protocolo.ENVIAR:
                        receberArquivo(entrada, saida);
                        break;
                    case Protocolo.LISTAR:
                        listarArquivos(saida);
                        break;
                    case Protocolo.BAIXAR:
                        enviarArquivo(entrada, saida);
                        break;
                    case Protocolo.SAIR:
                        conectado = false;
                        break;
                    default:
                        saida.writeBoolean(false);
                        saida.writeUTF("Comando desconhecido: " + comando);
                        saida.flush();
                }
            }
        } catch (EOFException erro) {
            System.out.println("O cliente encerrou a conexao.");
        } catch (IOException erro) {
            System.err.println("Erro ao atender cliente: " + erro.getMessage());
        } finally {
            System.out.println("Atendimento ao cliente finalizado.");
        }
    }

    /**
     * Recebe do cliente o nome, o tamanho e os bytes de um arquivo.
     *
     * O nome passa por uma validacao para impedir que caminhos como "../"
     * permitam gravar fora da pasta compartilhada. A leitura termina somente
     * quando exatamente a quantidade informada de bytes tiver sido recebida.
     */
    private static void receberArquivo(DataInputStream entrada,
                                       DataOutputStream saida) throws IOException {
        String nomeRecebido = entrada.readUTF();
        long tamanho = entrada.readLong();

        if (tamanho < 0) {
            saida.writeBoolean(false);
            saida.writeUTF("Tamanho de arquivo invalido.");
            saida.flush();
            return;
        }

        Path destino = caminhoSeguro(nomeRecebido);
        if (destino == null) {
            saida.writeBoolean(false);
            saida.writeUTF("Nome de arquivo invalido.");
            saida.flush();
            descartarBytes(entrada, tamanho);
            return;
        }

        try (BufferedOutputStream arquivo = new BufferedOutputStream(
                Files.newOutputStream(destino, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE))) {
            copiarQuantidadeExata(entrada, arquivo, tamanho);
        }

        saida.writeBoolean(true);
        saida.writeUTF("Arquivo '" + destino.getFileName() + "' recebido com sucesso.");
        saida.flush();
        System.out.println("Upload concluido: " + destino.getFileName()
                + " (" + tamanho + " bytes)");
    }

    /**
     * Envia ao cliente a lista de arquivos regulares da pasta compartilhada.
     *
     * Primeiro e enviado o total de arquivos. Depois, para cada item, seguem
     * seu nome e seu tamanho em bytes. Essa ordem faz parte do protocolo.
     */
    private static void listarArquivos(DataOutputStream saida) throws IOException {
        List<Path> arquivos;

        try (Stream<Path> itens = Files.list(PASTA_SERVIDOR)) {
            arquivos = itens
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(
                            caminho -> caminho.getFileName().toString().toLowerCase()))
                    .collect(Collectors.toList());
        }

        saida.writeInt(arquivos.size());
        for (Path arquivo : arquivos) {
            saida.writeUTF(arquivo.getFileName().toString());
            saida.writeLong(Files.size(arquivo));
        }
        saida.flush();
    }

    /**
     * Procura o arquivo solicitado e, quando ele existe, envia seu tamanho e
     * seus bytes ao cliente. Antes dos dados e enviado um booleano: true indica
     * sucesso; false indica que o arquivo nao foi encontrado ou e invalido.
     */
    private static void enviarArquivo(DataInputStream entrada,
                                      DataOutputStream saida) throws IOException {
        String nomeSolicitado = entrada.readUTF();
        Path arquivo = caminhoSeguro(nomeSolicitado);

        if (arquivo == null || !Files.isRegularFile(arquivo)) {
            saida.writeBoolean(false);
            saida.writeUTF("Arquivo nao encontrado no servidor.");
            saida.flush();
            return;
        }

        long tamanho = Files.size(arquivo);
        saida.writeBoolean(true);
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
        System.out.println("Download enviado: " + arquivo.getFileName()
                + " (" + tamanho + " bytes)");
    }

    /**
     * Transforma um nome recebido pela rede em um caminho seguro dentro da
     * pasta do servidor. Sao rejeitados nomes vazios e nomes que contenham
     * diretorios, evitando o ataque conhecido como path traversal.
     */
    private static Path caminhoSeguro(String nomeRecebido) {
        if (nomeRecebido == null || nomeRecebido.trim().isEmpty()) {
            return null;
        }

        Path somenteNome;
        try {
            somenteNome = Paths.get(nomeRecebido).getFileName();
        } catch (RuntimeException erro) {
            return null;
        }

        if (somenteNome == null || !somenteNome.toString().equals(nomeRecebido)) {
            return null;
        }

        Path pastaNormalizada = PASTA_SERVIDOR.toAbsolutePath().normalize();
        Path destino = pastaNormalizada.resolve(somenteNome).normalize();
        return destino.startsWith(pastaNormalizada) ? destino : null;
    }

    /**
     * Copia exatamente a quantidade esperada de bytes da rede para um arquivo.
     * Se a conexao terminar antes, a transferencia e considerada incompleta.
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

    /**
     * Consome bytes de um upload recusado para manter o fluxo do protocolo
     * alinhado e permitir que o cliente envie outro comando na mesma conexao.
     */
    private static void descartarBytes(DataInputStream entrada,
                                       long quantidade) throws IOException {
        byte[] buffer = new byte[TAMANHO_BUFFER];
        long restante = quantidade;

        while (restante > 0) {
            int lidos = entrada.read(buffer, 0,
                    (int) Math.min(buffer.length, restante));
            if (lidos == -1) {
                throw new EOFException("Conexao encerrada durante o descarte do upload.");
            }
            restante -= lidos;
        }
    }
}
