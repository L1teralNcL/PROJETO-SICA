/**
 * Centraliza os comandos trocados entre cliente e servidor.
 *
 * Manter os comandos em uma unica classe evita erros de digitacao e garante
 * que os dois lados da comunicacao usem exatamente os mesmos valores.
 */
public final class Protocolo {
    public static final String ENVIAR = "ENVIAR";
    public static final String LISTAR = "LISTAR";
    public static final String BAIXAR = "BAIXAR";
    public static final String SAIR = "SAIR";

    private Protocolo() {
        // Impede a criacao de objetos desta classe, pois ela contem apenas constantes.
    }
}
