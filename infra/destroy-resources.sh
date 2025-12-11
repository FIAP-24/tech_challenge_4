#!/bin/bash
# ATENÇÃO: Este script DESTRÓI todo o grupo de recursos.

# --- Personalize esta variável ---
# Deve ser o mesmo nome usado no script de criação.
RESOURCE_GROUP="rg-tech-challenge"
# --- Fim da personalização ---

echo "ATENÇÃO! Isso irá deletar permanentemente o grupo de recursos '$RESOURCE_GROUP' e todos os seus recursos."
read -p "Você tem certeza que deseja continuar? (s/n): " confirm

if [[ "$confirm" != "s" && "$confirm" != "S" ]]; then
  echo "Operação cancelada."
  exit 1
fi

echo "Iniciando exclusão do grupo de recursos: $RESOURCE_GROUP..."

# Exclui o grupo de recursos sem esperar (execução em background)
az group delete --name $RESOURCE_GROUP --yes --no-wait

echo "Exclusão iniciada. Pode levar alguns minutos para completar no portal do Azure."
