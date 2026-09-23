# SiCA - Sistema de Compartilhamento de Arquivos

Projeto em Java que utiliza sockets TCP para transferir arquivos entre um
cliente e um servidor. O cliente pode:

1. enviar um arquivo ao servidor;
2. listar os arquivos armazenados no servidor;
3. baixar um arquivo disponivel;
4. encerrar a conexao.

## Arquivos do projeto

- `Protocolo.java`: define os nomes dos comandos usados pelos dois lados.
- `ServidorSica.java`: recebe conexoes e executa as operacoes solicitadas.
- `ClienteSica.java`: mostra o menu e envia as solicitacoes do usuario.

As pastas `arquivos_servidor` e `downloads_cliente` sao criadas
automaticamente durante a execucao.

## Como compilar

Abra o terminal dentro da pasta do projeto e execute:

```text
javac Protocolo.java ServidorSica.java ClienteSica.java
```

## Como executar no mesmo computador

No primeiro terminal, inicie o servidor:

```text
java ServidorSica
```

Mantenha esse terminal aberto. Em um segundo terminal, execute o cliente:

```text
java ClienteSica
```

O cliente usara `localhost`, isto e, o proprio computador.

## Como executar em dois computadores

1. Conecte os dois computadores a mesma rede.
2. Execute `java ServidorSica` no computador que armazenara os arquivos.
3. Descubra o endereco IPv4 desse computador. No Windows, use `ipconfig`.
4. Se necessario, libere a porta TCP 5000 no firewall do servidor.
5. No outro computador, informe o IP ao iniciar o cliente:

```text
java ClienteSica 192.168.0.10
```

Substitua `192.168.0.10` pelo IPv4 real do servidor.

## Protocolo de comunicacao

Cliente e servidor precisam ler os dados na mesma ordem. O protocolo adotado e:

| Operacao | Cliente envia | Servidor responde |
| --- | --- | --- |
| ENVIAR | comando, nome, tamanho e bytes | sucesso/erro e mensagem |
| LISTAR | comando | quantidade; depois nome e tamanho de cada arquivo |
| BAIXAR | comando e nome | encontrado/nao encontrado; se encontrado, tamanho e bytes |
| SAIR | comando | encerra a conexao |

O tamanho e enviado antes dos bytes porque o TCP entrega um fluxo continuo. O
receptor precisa saber exatamente quantos bytes pertencem ao arquivo atual.

## Conceitos aplicados

- **Socket TCP:** representa uma conexao confiavel entre cliente e servidor.
- **ServerSocket:** fica escutando a porta 5000 e aceita novas conexoes.
- **Streams:** transportam comandos, nomes, tamanhos e conteudo binario.
- **Buffer:** transfere blocos de 8192 bytes, evitando carregar o arquivo todo
  na memoria.
- **Threads:** o servidor usa um pool para atender varios clientes.
- **try-with-resources:** fecha sockets, streams e arquivos automaticamente.
- **Validacao de caminho:** impede que um cliente grave fora da pasta permitida.

## Exemplo de uso

```text
===== SiCA - Cliente =====
1 - Enviar arquivo
2 - Listar arquivos do servidor
3 - Baixar arquivo
0 - Sair
Escolha uma opcao: 1
Caminho do arquivo que sera enviado: C:\\Users\\Aluno\\Desktop\\exemplo.pdf
SUCESSO: Arquivo 'exemplo.pdf' recebido com sucesso.
```

Depois, a opcao 2 mostra o arquivo, e a opcao 3 salva uma copia dele na pasta
`downloads_cliente`.
