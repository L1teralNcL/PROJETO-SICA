# Relatório explicativo - SiCA

## 1. Objetivo

O projeto implementa um Sistema de Compartilhamento de Arquivos, chamado SiCA,
em Java. A comunicação ocorre entre duas aplicações: um servidor, responsável
por armazenar os arquivos, e um cliente, usado para solicitar operações.

As operações exigidas são:

1. enviar um arquivo ao servidor;
2. listar os arquivos presentes no servidor;
3. baixar um arquivo disponível no servidor.

## 2. Por que foram usados sockets TCP?

Um socket é um ponto de comunicação entre dois programas conectados por uma
rede. O servidor cria um `ServerSocket` na porta 5000 e fica aguardando
conexões. O cliente cria um `Socket` informando o endereço do servidor e a
mesma porta.

O TCP foi escolhido porque estabelece uma conexão e entrega os bytes na ordem
em que foram enviados. Isso é importante na transferência de arquivos: bytes
perdidos, repetidos ou fora de ordem poderiam corromper o conteúdo.

O TCP, porém, fornece um fluxo contínuo de bytes e não identifica sozinho onde
um arquivo começa ou termina. Por isso, a aplicação define um protocolo próprio
e sempre envia o tamanho do arquivo antes de enviar o conteúdo.

## 3. Arquitetura da solução

### Cliente

O `ClienteSica` apresenta um menu ao usuário. Conforme a opção escolhida, envia
um comando ao servidor e, em seguida, os dados necessários para aquela
operação. Ele também cria automaticamente a pasta `downloads_cliente`, na qual
grava os arquivos baixados.

### Servidor

O `ServidorSica` fica ouvindo a porta 5000. Quando aceita uma conexão, entrega o
atendimento a uma thread do `ExecutorService`. Assim, o servidor pode manter
mais de um cliente conectado.

Os arquivos recebidos são colocados em `arquivos_servidor`, pasta criada
automaticamente no diretório em que o servidor foi iniciado.

### Protocolo

A classe `Protocolo` contém as constantes `ENVIAR`, `LISTAR`, `BAIXAR` e `SAIR`.
Centralizar esses textos reduz a possibilidade de o cliente escrever um comando
diferente daquele esperado pelo servidor.

## 4. Ordem dos dados enviados

| Operação | Dados enviados pelo cliente | Resposta do servidor |
| --- | --- | --- |
| Enviar | `ENVIAR`, nome, tamanho e bytes | booleano de sucesso e mensagem |
| Listar | `LISTAR` | quantidade; depois nome e tamanho de cada arquivo |
| Baixar | `BAIXAR` e nome | booleano; se existir, tamanho e bytes |
| Sair | `SAIR` | a conexão é encerrada |

A ordem precisa ser exatamente igual nos dois lados. Por exemplo, se o cliente
usar `writeUTF()` e depois `writeLong()`, o servidor deve usar `readUTF()` e
depois `readLong()`. Ler em outra ordem faria o servidor interpretar bytes de
texto como se fossem um número, quebrando a comunicação.

## 5. Funcionamento de cada operação

### Envio de arquivo (upload)

1. O cliente verifica se o caminho informado representa um arquivo real.
2. Envia o comando `ENVIAR`.
3. Envia o nome do arquivo e o tamanho em bytes.
4. Abre o arquivo e envia seu conteúdo em blocos de 8192 bytes.
5. O servidor valida o nome, cria o arquivo na pasta compartilhada e lê
   exatamente a quantidade de bytes anunciada.
6. O servidor confirma se a operação teve sucesso.

### Listagem

1. O cliente envia `LISTAR`.
2. O servidor procura somente arquivos regulares em `arquivos_servidor`.
3. O servidor ordena os nomes e envia a quantidade encontrada.
4. Para cada item, envia o nome e seu tamanho.
5. O cliente lê e exibe os registros.

### Download

1. O cliente envia `BAIXAR` e o nome desejado.
2. O servidor valida o nome e verifica se o arquivo existe.
3. Caso não exista, envia `false` e uma mensagem de erro.
4. Caso exista, envia `true`, o tamanho e os bytes do arquivo.
5. O cliente grava exatamente a quantidade recebida em `downloads_cliente`.

## 6. Por que usar um buffer?

Os arquivos não são carregados inteiros na memória. O código utiliza um vetor
de 8192 bytes e transfere vários blocos até terminar. Desse modo, um arquivo
grande pode ser copiado sem ocupar uma quantidade de memória equivalente ao seu
tamanho total.

Também não se pode supor que uma chamada a `read()` receberá tudo de uma vez.
A rede pode dividir os dados em vários pacotes. Por isso, o método
`copiarQuantidadeExata` repete a leitura e controla quantos bytes ainda faltam.

## 7. Tratamento de recursos e erros

O código utiliza `try-with-resources`. Essa estrutura fecha automaticamente
sockets, streams e arquivos, inclusive quando ocorre uma exceção. Isso evita
vazamento de recursos e arquivos que permanecem bloqueados no sistema.

Erros de conexão e de leitura são capturados como `IOException` e apresentados
ao usuário. Um encerramento inesperado da conexão durante uma transferência é
identificado por `EOFException`.

## 8. Segurança aplicada ao nome do arquivo

O servidor não aceita caminhos, apenas nomes simples. Isso impede que uma
entrada como `../arquivo.txt` seja usada para tentar gravar ou ler conteúdo fora
de `arquivos_servidor`. Essa validação reduz o risco de *path traversal*.

Essa solução é adequada a uma atividade acadêmica. Em um sistema real, também
seriam necessários autenticação, criptografia TLS, permissões por usuário,
limite de tamanho, registro de auditoria e tratamento de arquivos duplicados.

## 9. Exemplo resumido de execução

Primeiro, em um terminal:

```text
javac Protocolo.java ServidorSica.java ClienteSica.java
java ServidorSica
```

Depois, em outro terminal aberto na mesma pasta:

```text
java ClienteSica
```

Exemplo no cliente:

```text
===== SiCA - Cliente =====
1 - Enviar arquivo
2 - Listar arquivos do servidor
3 - Baixar arquivo
0 - Sair
Escolha uma opcao: 2

Arquivos disponiveis:
1 - exemplo.pdf (250.4 KB)
```

## 10. Resultado da validação

A solução foi compilada e testada de ponta a ponta. No teste, um arquivo foi
enviado, apareceu na listagem e foi baixado. O original, a cópia do servidor e
a cópia baixada apresentaram o mesmo hash SHA-256, demonstrando que os bytes
foram preservados durante as duas transferências.
