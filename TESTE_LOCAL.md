# 🧪 Guia de Teste Local

Este guia explica como testar as Azure Functions localmente antes de fazer deploy no Azure.

## 📋 Pré-requisitos

1. **Java 21** instalado
2. **Maven 3.6+** instalado
3. **Azure Functions Core Tools v4** instalado
   - Windows: `npm install -g azure-functions-core-tools@4 --unsafe-perm true`
   - macOS: `brew tap azure/functions && brew install azure-functions-core-tools@4`
   - Linux: [Instruções](https://docs.microsoft.com/azure/azure-functions/functions-run-local#v2)
4. **Azure Storage Emulator** ou **Azurite** (para simular Azure Storage localmente)
   - Instalar Azurite: `npm install -g azurite`
   - Ou usar Docker: `docker run -p 10000:10000 -p 10001:10001 -p 10002:10002 mcr.microsoft.com/azurite`

## 🚀 Opção 1: Usando Azure Functions Core Tools (Recomendado)

### 1. Iniciar Azurite (Emulador de Storage)

Em um terminal separado:

```bash
# Se instalou via npm
azurite --silent --location ~/azurite --debug ~/azurite/debug.log

# Ou se estiver usando Docker
docker run -p 10000:10000 -p 10001:10001 -p 10002:10002 mcr.microsoft.com/azurite
```

### 2. Configurar local.settings.json

O arquivo `src/main/resources/local.settings.json` já está configurado, mas você pode ajustar:

```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "UseDevelopmentStorage=true",
    "FUNCTIONS_WORKER_RUNTIME": "java",
    "SENDGRID_API_KEY": "sua-chave-aqui-ou-deixe-vazio-para-testes",
    "FROM_EMAIL": "seu-email@exemplo.com",
    "ADMIN_EMAIL": "admin@exemplo.com",
    "APPLICATIONINSIGHTS_CONNECTION_STRING": ""
  }
}
```

**Nota:** `UseDevelopmentStorage=true` usa o Azurite automaticamente quando ele está rodando.

### 3. Compilar o Projeto

```bash
mvn clean package
```

### 4. Executar Localmente

```bash
# Navegue até o diretório onde o host.json está
cd src/main/resources

# Execute as functions
func start --java
```

Ou, se preferir executar a partir da raiz do projeto:

```bash
func start --java --script-root target/azure-functions/feedback-platform-*/
```

### 5. Testar a API

Após iniciar, você verá algo como:

```
Functions:
        AvaliacaoHandler: [POST] http://localhost:7071/api/avaliacao
```

Teste com curl ou Postman:

```bash
curl -X POST http://localhost:7071/api/avaliacao \
  -H "Content-Type: application/json" \
  -H "x-functions-key: <FUNCTION_KEY>" \
  -d '{
    "descricao": "Excelente curso, muito didático!",
    "nota": 9
  }'
```

**Para obter a FUNCTION_KEY:**
- Verifique o output do `func start` - ele mostra as chaves
- Ou acesse: `http://localhost:7071/admin/functions/AvaliacaoHandler` no navegador

### 6. Testar Avaliação Crítica (Nota <= 3)

```bash
curl -X POST http://localhost:7071/api/avaliacao \
  -H "Content-Type: application/json" \
  -H "x-functions-key: <FUNCTION_KEY>" \
  -d '{
    "descricao": "Curso muito ruim, conteúdo desatualizado",
    "nota": 2
  }'
```

Isso deve disparar um e-mail (se SENDGRID_API_KEY estiver configurado).

---

## 🚀 Opção 2: Usando Quarkus Dev Mode (Alternativa)

Para desenvolvimento mais rápido com hot reload:

```bash
mvn quarkus:dev
```

**Nota:** Esta opção pode não funcionar perfeitamente com Azure Functions, pois o Quarkus dev mode é otimizado para aplicações web tradicionais. A Opção 1 é mais adequada para Azure Functions.

---

## 🧪 Testando o Relatório Semanal

O Timer Trigger não executa automaticamente em modo local por padrão. Para testar:

### Opção A: Executar Manualmente via Portal (após deploy)

Após fazer deploy no Azure, você pode executar manualmente pelo Portal Azure.

### Opção B: Forçar Execução Local (Desenvolvimento)

1. Modifique temporariamente o `RelatorioFunction.java` para aceitar HTTP GET:

```java
@FunctionName("RelatorioSemanalHandler")
public HttpResponseMessage run(
        @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "relatorio/teste"
        ) HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {
    // ... código existente ...
}
```

2. Acesse: `http://localhost:7071/api/relatorio/teste`

### Opção C: Usar Azure Functions Admin API

```bash
curl -X POST http://localhost:7071/admin/functions/RelatorioSemanalHandler \
  -H "x-functions-key: <MASTER_KEY>"
```

---

## 🔍 Verificando os Dados no Storage

### Usando Azure Storage Explorer

1. Instale o [Azure Storage Explorer](https://azure.microsoft.com/features/storage-explorer/)
2. Conecte ao Azurite:
   - Account name: `devstoreaccount1`
   - Account key: `Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==`
   - Blob endpoint: `http://127.0.0.1:10000`
   - Queue endpoint: `http://127.0.0.1:10001`
   - Table endpoint: `http://127.0.0.1:10002`

### Usando Azure CLI

```bash
# Listar tabelas
az storage table list \
  --connection-string "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;TableEndpoint=http://127.0.0.1:10002/devstoreaccount1;"

# Consultar avaliações
az storage entity query \
  --table-name avaliacoes \
  --connection-string "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;TableEndpoint=http://127.0.0.1:10002/devstoreaccount1;"
```

---

## 🐛 Solução de Problemas

### Erro: "Cannot find module 'azure-functions-core-tools'"

```bash
npm install -g azure-functions-core-tools@4 --unsafe-perm true
```

### Erro: "Azurite is not running"

Certifique-se de que o Azurite está rodando em um terminal separado antes de iniciar as functions.

### Erro: "Port 7071 already in use"

```bash
# Encontre o processo usando a porta
lsof -i :7071

# Mate o processo ou use outra porta
func start --port 7072
```

### Erro de conexão com Storage

Verifique se:
1. Azurite está rodando
2. `AzureWebJobsStorage` está configurado como `UseDevelopmentStorage=true`
3. As portas 10000, 10001, 10002 estão livres

### E-mails não são enviados localmente

Isso é esperado se `SENDGRID_API_KEY` estiver vazio. Para testar envio de e-mails:
1. Configure uma chave válida do SendGrid no `local.settings.json`
2. Ou verifique os logs para confirmar que o código está tentando enviar

---

## 📝 Exemplos de Teste

### Teste 1: Avaliação Normal

```bash
curl -X POST http://localhost:7071/api/avaliacao \
  -H "Content-Type: application/json" \
  -H "x-functions-key: <KEY>" \
  -d '{
    "descricao": "Curso muito bom, aprendi bastante",
    "nota": 8
  }'
```

**Esperado:** HTTP 201, avaliação salva no storage

### Teste 2: Avaliação Crítica

```bash
curl -X POST http://localhost:7071/api/avaliacao \
  -H "Content-Type: application/json" \
  -H "x-functions-key: <KEY>" \
  -d '{
    "descricao": "Muito ruim, não recomendo",
    "nota": 1
  }'
```

**Esperado:** HTTP 201, avaliação salva + e-mail enviado (se SendGrid configurado)

### Teste 3: Validação de Erro

```bash
curl -X POST http://localhost:7071/api/avaliacao \
  -H "Content-Type: application/json" \
  -H "x-functions-key: <KEY>" \
  -d '{
    "descricao": "",
    "nota": 15
  }'
```

**Esperado:** HTTP 400, erro de validação

---

## 📚 Referências

- [Azure Functions Core Tools](https://docs.microsoft.com/azure/azure-functions/functions-run-local)
- [Azurite Documentation](https://github.com/Azure/Azurite)
- [Quarkus Azure Functions](https://quarkus.io/guides/azure-functions-http)

