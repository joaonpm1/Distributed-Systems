# SD Grupo 10

Sistema distribuido para gestão de séries temporais com suporte a múltiplos clientes concorrentes.

## Requisitos

- Java 17+
- Maven 3.6+

## Compilação

```bash
mvn compile
```

## Servidor

Iniciar o servidor com configurações por defeito:

```bash
java -cp target/classes server.Server
```

### Opções do Servidor

| Opção | Descrição | Valor por defeito |
|-------|-----------|-------------------|
| `-p, --port <port>` | Porta TCP | 8080 |
| `-D, --days <n>` | Dias de historico | 30 |
| `-S, --memory <n>` | Series em memoria | 10 |
| `-d, --data <path>` | Directorio de dados | data |
| `-r, --recover` | Recuperar estado do disco | - |
| `-h, --help` | Mostrar ajuda | - |

Exemplo com opções personalizadas:

```bash
java -cp target/classes server.Server -p 9090 -D 60 -S 20
```

## Cliente

Iniciar o cliente interativo:

```bash
java -cp target/classes client.ClientUI
```

### Comandos do Cliente

| Comando | Descrição |
|---------|-----------|
| `connect <host> <port>` | Conectar ao servidor |
| `register <user> <pass>` | Registar novo utilizador |
| `login <user> <pass>` | Autenticar utilizador |
| `add <produto> <qtd> <preco>` | Adicionar evento |
| `newday` | Avancar para novo dia |
| `qty <produto> <dias>` | Consultar quantidade |
| `vol <produto> <dias>` | Consultar volume |
| `avg <produto> <dias>` | Consultar media |
| `max <produto> <dias>` | Consultar maximo |
| `filter <produto> <dias> <min> <max>` | Filtrar eventos |
| `simul <produtos> <dias>` | Eventos simultaneos |
| `consec <produtos> <dias>` | Eventos consecutivos |
| `help` | Mostrar ajuda |
| `quit` | Sair |

## Benchmarks

### Modo Interativo

```bash
java -cp target/classes benchmark.BenchmarkRunner
```

### Executar Benchmarks Especificos

```bash
# Todos os benchmarks
java -cp target/classes benchmark.BenchmarkRunner --all

# Apenas benchmark de carga
java -cp target/classes benchmark.BenchmarkRunner --load

# Apenas benchmark de escalabilidade
java -cp target/classes benchmark.BenchmarkRunner --scale

# Apenas benchmark de robustez
java -cp target/classes benchmark.BenchmarkRunner --robust
```

### Opções dos Benchmarks

| Opção | Descrição | Valor por defeito |
|-------|-----------|-------------------|
| `--host <host>` | Host do servidor | localhost |
| `--port <port>` | Porta do servidor | 8080 |
| `--clients <n>` | Numero de clientes | 16 |
| `--ops <n>` | Operacoes por cliente | 1000 |

Exemplo:

```bash
java -cp target/classes benchmark.BenchmarkRunner --load --host localhost --port 8080
```

## Estrutura do Projeto

```
src/main/java/
  server/          # Servidor e handlers
  client/          # Cliente e biblioteca
  common/          # Protocolo e modelos partilhados
  benchmark/       # Suite de benchmarks
```
