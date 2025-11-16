# Script para criar a infraestrutura mínima no Azure

# --- Personalize estas variáveis ---
# IMPORTANTE: Os nomes de storage e function app devem ser ÚNICOS globalmente.
LOCATION="eastus" # Região do Azure (ex: eastus, brazilsouth)
RESOURCE_GROUP="rg-tech-challenge"
STORAGE_NAME="sttechchallenge$(openssl rand -hex 4)" # Nome único para o Storage
FUNCTION_APP_NAME="func-tech-challenge-$(openssl rand -hex 4)" # Nome único para o Function App
# E-mails para teste (altere antes de rodar)
ADMIN_EMAIL="silveira.s.renan@gmail.com"
FROM_EMAIL="silveira.s.renan@gmail.com"
# --- Fim da personalização ---

echo "Iniciando criação de recursos..."
echo "Grupo de Recursos: $RESOURCE_GROUP"
echo "Storage Account: $STORAGE_NAME"
echo "Function App: $FUNCTION_APP_NAME"
echo "Localização: $LOCATION"

# 1. Login (necessário se não estiver logado)
az login

# 2. Criar Grupo de Recursos
echo "Criando Grupo de Recursos..."
az group create --name $RESOURCE_GROUP --location $LOCATION

# 3. Criar Storage Account (necessário para o Function App e para nossos dados)
echo "Criando Storage Account..."
az storage account create \
  --name $STORAGE_NAME \
  --location $LOCATION \
  --resource-group $RESOURCE_GROUP \
  --sku Standard_LRS \
  --allow-blob-public-access false

# 4. Criar Tabela de 'avaliacoes'
echo "Obtendo connection string do storage..."
STORAGE_CONNECTION_STRING=$(az storage account show-connection-string --name $STORAGE_NAME --resource-group $RESOURCE_GROUP --query connectionString -o tsv)

echo "Criando Tabela 'avaliacoes'..."
az storage table create --name "avaliacoes" --connection-string "$STORAGE_CONNECTION_STRING"

# 4.1 Criar Tabela de 'relatorios'
echo "Criando Tabela 'relatorios'..."
az storage table create --name "relatorios" --connection-string "$STORAGE_CONNECTION_STRING"

# 5. Criar Application Insights (Monitoramento )
echo "Criando Application Insights..."
az monitor app-insights component create \
  --app $FUNCTION_APP_NAME \
  --location $LOCATION \
  --resource-group $RESOURCE_GROUP \
  --kind "web" \
  --application-type "web"

APP_INSIGHTS_CONNECTION_STRING=$(az monitor app-insights component show --app $FUNCTION_APP_NAME -g $RESOURCE_GROUP --query "connectionString" -o tsv)

# 6. Criar Function App (Plano Consumption - Serverless [cite: 17])
echo "Criando Function App..."
az functionapp create \
  --name $FUNCTION_APP_NAME \
  --storage-account $STORAGE_NAME \
  --resource-group $RESOURCE_GROUP \
  --consumption-plan-location $LOCATION \
  --functions-version 4 \
  --runtime "java" \
  --runtime-version "21" \
  --os-type "linux" \
  --app-insights-key ""

# 6.1. Configurar Application Insights (connection string moderna)
echo "Configurando Application Insights (APPLICATIONINSIGHTS_CONNECTION_STRING)"
az functionapp config appsettings set \
  --name $FUNCTION_APP_NAME \
  --resource-group $RESOURCE_GROUP \
  --settings APPLICATIONINSIGHTS_CONNECTION_STRING="$APP_INSIGHTS_CONNECTION_STRING"

# 6.2. Configurar variáveis de ambiente necessárias para o app
echo "Definindo variáveis de ambiente do Function App (ADMIN_EMAIL, FROM_EMAIL, SENDGRID_API_KEY)"
az functionapp config appsettings set \
  --name $FUNCTION_APP_NAME \
  --resource-group $RESOURCE_GROUP \
  --settings ADMIN_EMAIL="$ADMIN_EMAIL" FROM_EMAIL="$FROM_EMAIL" SENDGRID_API_KEY="defina_no_portal_ou_gh_secrets"

# 7. Configurar CORS (se for testar de uma UI web)
az functionapp cors add --name $FUNCTION_APP_NAME -g $RESOURCE_GROUP --allowed-origins "*"

echo "--- INFRAESTRUTURA CRIADA COM SUCESSO ---"
echo ""
echo "Guarde estas informações:"
echo "Grupo de Recursos: $RESOURCE_GROUP"
echo "Nome do Function App: $FUNCTION_APP_NAME"
echo "Nome do Storage Account: $STORAGE_NAME"
echo "Application Insights Connection String: $APP_INSIGHTS_CONNECTION_STRING"
echo ""
echo "Execute o script 'destroy-resources.sh' para remover tudo."
