# Tech Challenge Fase 4 - Plataforma de Feedback (Quarkus + Azure)

[cite_start]Este projeto implementa a plataforma de feedback descrita no Tech Challenge [cite: 2, 4][cite_start], utilizando uma arquitetura 100% serverless no Azure [cite: 17][cite_start], Java com Quarkus, e deploy automatizado com GitHub Actions.

[cite_start]O foco é a utilização de serviços de baixo custo (Plano Consumption e Azure Storage Tables) para respeitar os limites de créditos[cite: 11].

## 📋 Funcionalidades

1.  [cite_start]**API de Avaliação** (`POST /api/avaliacao`): Recebe um feedback (JSON com `descricao` e `nota` 0-10)[cite: 32, 35, 36].
    - Valida os dados de entrada
    - Calcula automaticamente o nível de urgência baseado na nota
    - Persiste no Azure Storage Tables
2.  **Persistência:** Salva as avaliações e relatórios no Azure Storage Tables (custo mínimo).
    - Tabela `avaliacoes`: armazena todos os feedbacks recebidos
    - Tabela `relatorios`: armazena os relatórios semanais gerados
3.  [cite_start]**Notificação Crítica:** Se a `nota` for <= 3, dispara um e-mail de alerta imediato para o administrador[cite: 15, 37].
4.  [cite_start]**Relatório Semanal:** Uma função (TimerTrigger) executa semanalmente (toda segunda-feira às 9h), calcula métricas e envia um resumo por e-mail[cite: 41, 45, 46]:
    - **Métricas Gerais:** Total de avaliações, média das notas, nota mais alta/baixa
    - **Distribuição por Urgência:** Contagem de avaliações por nível (NORMAL, ALTA, CRITICO)
    - **Análise de Comentários Recorrentes:** Identifica as palavras e frases mais frequentes nos feedbacks
    - **Persistência:** Salva o relatório na tabela `relatorios` para histórico

## 🏛️ Arquitetura

* **Compute:** Azure Functions (Plano Consumption) - Java 21 + Quarkus
* **Persistência:** Azure Storage Tables (Tabelas `avaliacoes` e `relatorios`)
* **Análise de Texto:** Processamento de comentários recorrentes (stop words, n-grams)
* [cite_start]**Monitoramento:** Application Insights
* **E-mail:** SendGrid (Nível gratuito)
* **CI/CD:** GitHub Actions
* **Injeção de Dependência:** Lombok `@RequiredArgsConstructor` (constructor injection)

---

## 🚀 Guia de Deploy (Passo-a-Passo)

Siga estes passos para configurar a infraestrutura no Azure e o deploy automático.

### 1. Pré-requisitos Locais

* [Git](https://git-scm.com/)
* [Azure CLI](https://docs.microsoft.com/pt-br/cli/azure/install-azure-cli)
* [Java 21 (JDK)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
* [Maven](https://maven.apache.org/download.cgi)
* Uma conta [SendGrid](https://sendgrid.com/) (nível gratuito) com um **Sender Verificado**.
* (Opcional para testes locais) [Azure Functions Core Tools](https://docs.microsoft.com/azure/azure-functions/functions-run-local) e [Azurite](https://github.com/Azure/Azurite)

### 2. Criação da Infraestrutura no Azure

Primeiro, clone este repositório. Em seguida, execute o script de criação de infraestrutura.

```bash
# Faça login na sua conta Azure
az login

# Navegue até a pasta de infra
cd infra

# Dê permissão de execução ao script
chmod +x create-resources.sh

# Execute o script
./create-resources.sh
```

O script irá criar:
- Resource Group, Storage Account (com tabelas `avaliacoes` e `relatorios`), Application Insights e um Azure Function App (Java 21, Linux, plano Consumption).
- Também define App Settings básicos (`APPLICATIONINSIGHTS_CONNECTION_STRING`, `ADMIN_EMAIL`, `FROM_EMAIL` e um placeholder para `SENDGRID_API_KEY`).
- As tabelas são criadas automaticamente na primeira execução das funções, caso não existam.

Anote o nome do Function App impresso ao final, pois será usado nos próximos passos.

---

### 3. Configuração no SendGrid (e-mail)

Para que os e-mails funcionem (alertas críticos e relatório semanal):

1. Crie uma conta no SendGrid (plano gratuito) e faça login no painel.
2. Verifique um remetente:
   - Opção rápida: Sender Identity único (Single Sender Verification) com o e-mail que você controlará. Esse será o `FROM_EMAIL`.
   - Opção recomendada: Domain Authentication (requer ajustar DNS do seu domínio).
3. Gere uma API Key:
   - Acesse: Settings > API Keys > Create API Key.
   - Permissões: “Restricted Access” com “Mail Send: Full Access”.
   - Copie a chave (você não verá novamente).
4. Guarde:
   - `SENDGRID_API_KEY`
   - `FROM_EMAIL` (o remetente verificado)
   - `ADMIN_EMAIL` (quem receberá os alertas e relatórios)

---

### 4. Configurar App Settings no Azure Function App

No Portal Azure:
1. Acesse o recurso do seu Function App > Settings > Configuration.
2. Em Application settings, crie/atualize as chaves abaixo:
   - `ADMIN_EMAIL` = email do administrador que receberá alertas/relatórios.
   - `FROM_EMAIL` = remetente verificado no SendGrid.
   - `SENDGRID_API_KEY` = a chave criada no SendGrid.
   - `APPLICATIONINSIGHTS_CONNECTION_STRING` já deve estar definido pelo script.
   - `AzureWebJobsStorage` já está configurado ao criar o Function App (não altere).
3. Salve e aplique o restart quando solicitado.

Se quiser fazer via CLI:
```bash
az functionapp config appsettings set \
  -g <SEU_RESOURCE_GROUP> \
  -n <SEU_FUNCTION_APP_NAME> \
  --settings ADMIN_EMAIL="seu-admin@exemplo.com" FROM_EMAIL="seu-remetente@exemplo.com" SENDGRID_API_KEY="SG.xxxxx"
```

Para testes a partir de uma UI Web, você pode liberar CORS (use apenas durante desenvolvimento):
```bash
az functionapp cors add -g <SEU_RESOURCE_GROUP> -n <SEU_FUNCTION_APP_NAME> --allowed-origins "*"
```

---

### 5. Configuração do Git/GitHub e Secrets (CI/CD)

1. Suba este código para um repositório no GitHub (branch `main`):
   - git init, git remote add origin, git add ., git commit -m "init", git push -u origin main.
2. No GitHub, vá em Settings > Secrets and variables > Actions > New repository secret e cadastre:
   - `FUNCTION_APP_NAME` = Nome do Function App criado (ex.: `func-tech-challenge-xxxx`).
   - `AZURE_CREDENTIALS` = Publish Profile do Function App:
     - No Portal Azure: Function App > Overview > Get publish profile > baixe o arquivo `.PublishSettings` e cole o conteúdo inteiro como valor do secret.
   - (Opcional, se preferir injetar via pipeline) `SENDGRID_API_KEY`, `ADMIN_EMAIL`, `FROM_EMAIL`.

O workflow em `.github/workflows/deploy.yml` já está preparado para:
- Buildar o projeto com Maven/Quarkus para Azure Functions.
- Publicar usando o `publish-profile` armazenado em `AZURE_CREDENTIALS`.
- Usar `FUNCTION_APP_NAME` para direcionar o deploy.

Se optar por enviar variáveis sensíveis via Azure App Settings (recomendado), não é necessário adicioná-las como secrets no GitHub.

---

### 6. Disparar o Deploy

Faça um commit na branch `main` ou acione manualmente um push. O GitHub Actions rodará o job “Deploy Quarkus App to Azure Functions”.

Após a execução, valide no Portal Azure:
- Function App > Functions: a função HTTP deve aparecer (ex.: `httpAvaliacao`).
- Function App > Configuration: app settings presentes.
- Application Insights: logs e traces sendo coletados.

---

### 7. Testes Locais (Opcional)

Para testar a aplicação localmente antes do deploy:

1. **Instalar Azure Functions Core Tools:**
   ```bash
   npm install -g azure-functions-core-tools@4
   ```

2. **Instalar e iniciar Azurite (emulador do Azure Storage):**
   ```bash
   npm install -g azurite
   azurite --silent --location ~/azurite
   ```

3. **Configurar variáveis de ambiente locais:**
   - Edite `src/main/resources/local.settings.json`
   - Configure `SENDGRID_API_KEY`, `FROM_EMAIL`, `ADMIN_EMAIL`

4. **Executar as funções localmente:**
   ```bash
   mvn clean package
   cd target/azure-functions/feedback-platform-1.0.0
   func start --java
   ```

5. **Testar a API:**
   ```bash
   curl -X POST http://localhost:7071/api/avaliacao \
     -H "Content-Type: application/json" \
     -d '{"descricao": "Teste de feedback", "nota": 5}'
   ```

Para mais detalhes, consulte o arquivo `TESTE_LOCAL.md`.

---

### 8. Testes Rápidos

1. Invocar a API de avaliação (HTTP Trigger):
   - URL típica: `https://<SEU_FUNCTION_APP_NAME>.azurewebsites.net/api/avaliacao`
   - Corpo JSON:
   ```json
   {
     "descricao": "Gostei do atendimento",
     "nota": 3
   }
   ```
   - Esperado: HTTP 201. Se `nota <= 3`, um e-mail é enviado ao `ADMIN_EMAIL`.

2. Relatório semanal (Timer Trigger):
   - O job roda automaticamente pela CRON configurada na função. Você pode executar manualmente (Run) pelo Portal Azure > Functions > sua função de relatório.

---

### 9. Solução de Problemas (FAQ)

- Deploy falhou no GitHub Actions: verifique se os secrets `FUNCTION_APP_NAME` e `AZURE_CREDENTIALS` estão corretos. Baixe novamente o Publish Profile se necessário.
- E-mail não chega:
  - Confirme `FROM_EMAIL` verificado no SendGrid.
  - Confira `SENDGRID_API_KEY` em App Settings do Function App.
  - Verifique o log no Application Insights e no SendGrid (Activity Feed).
- Erro 500 na função HTTP:
  - Cheque se a Storage Account existe e `AzureWebJobsStorage` está presente no Function App.
  - Confirme que as tabelas `avaliacoes` e `relatorios` existem (o script cria automaticamente). Caso tenha criado manualmente, rode: `az storage table create --name avaliacoes` e `--name relatorios` usando a connection string do storage.

---

### 10. Estrutura do Projeto

```
feedback-platform/
├── src/main/java/br/com/fiap/techchallenge/
│   ├── functions/
│   │   ├── AvaliacaoFunction.java      # HTTP Trigger - Recebe avaliações
│   │   └── RelatorioFunction.java     # Timer Trigger - Gera relatórios semanais
│   ├── model/
│   │   ├── Avaliacao.java             # Modelo de dados para avaliações
│   │   └── RelatorioSemanal.java      # Modelo de dados para relatórios
│   ├── repository/
│   │   └── StorageTableRepository.java # Repositório para Azure Storage Tables
│   └── service/
│       ├── AnaliseTextoService.java   # Análise de comentários recorrentes
│       ├── EmailService.java           # Envio de e-mails via SendGrid
│       └── RelatorioService.java      # Geração de relatórios semanais
├── src/main/resources/
│   ├── application.properties         # Configurações do Quarkus
│   └── local.settings.json           # Configurações locais (Azure Functions)
├── infra/
│   └── create-resources.sh            # Script de criação de infraestrutura
├── .github/workflows/
│   └── deploy.yml                     # Pipeline CI/CD
├── pom.xml                            # Configuração Maven
└── README.md                          # Este arquivo
```

### 11. Tecnologias e Dependências

* **Java 21** - Linguagem de programação
* **Quarkus 3.6.4** - Framework Java otimizado para cloud
* **Azure Functions Java Library 3.0.0** - SDK para Azure Functions
* **Azure Storage Tables 12.4.3** - Cliente para Azure Storage Tables
* **SendGrid 4.10.2** - Cliente para envio de e-mails
* **Application Insights 3.4.19** - Monitoramento e telemetria
* **Lombok 1.18.30** - Redução de boilerplate (constructor injection)
* **Hibernate Validator** - Validação de dados

### 12. Funcionalidades Detalhadas

#### Análise de Comentários Recorrentes

O sistema analisa automaticamente os comentários dos feedbacks para identificar padrões:

- **Palavras Recorrentes:** Identifica as 10 palavras mais frequentes (após remover stop words em português)
- **Frases Recorrentes:** Identifica bigramas e trigramas (frases de 2-3 palavras) mais frequentes
- **Processamento:** Normaliza texto, remove pontuação e palavras comuns (a, o, de, para, etc.)
- **Resultado:** Incluído no relatório semanal enviado por e-mail

#### Níveis de Urgência

As avaliações são classificadas automaticamente:

- **CRITICO:** Nota <= 3 (dispara e-mail imediato)
- **ALTA:** Nota entre 4 e 6
- **NORMAL:** Nota >= 7

### 13. Referências úteis

- [Azure Functions Java 21 (Linux, Consumption)](https://docs.microsoft.com/azure/azure-functions/functions-reference-java)
- [Quarkus Azure Functions HTTP Extension](https://quarkus.io/guides/azure-functions-http)
- [SendGrid: Single Sender Verification e API Keys](https://docs.sendgrid.com/for-developers/sending-email/sender-identity)
- [Azure Storage Tables](https://docs.microsoft.com/azure/storage/tables/)
- [Application Insights](https://docs.microsoft.com/azure/azure-monitor/app/app-insights-overview)
