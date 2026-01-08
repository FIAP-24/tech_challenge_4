package br.com.fiap.techchallenge.repository;

import br.com.fiap.techchallenge.model.Avaliacao;
import br.com.fiap.techchallenge.model.RelatorioSemanal;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Repositório para operações no Azure Storage Tables
 * Gerencia persistência de avaliações e relatórios
 */
@ApplicationScoped
public class StorageTableRepository {

    private static final Logger LOG = Logger.getLogger(StorageTableRepository.class);
    private static final String TABLE_AVALIACOES = "avaliacoes";
    private static final String TABLE_RELATORIOS = "relatorios";
    private static final String PARTITION_KEY_AVALIACOES = "Java";
    private static final String PARTITION_KEY_RELATORIOS = "Semanal";

    private volatile TableServiceClient tableServiceClient;

    /**
     * Inicializa a conexão com o Azure Storage Tables
     */
    private void initialize() {
        if (tableServiceClient == null) {
            synchronized (this) {
                if (tableServiceClient == null) {
                    LOG.info("Inicializando conexão com Azure Storage Tables");
                    String connectionString = System.getenv("AzureWebJobsStorage");
                    if (connectionString == null || connectionString.isBlank()) {
                        connectionString = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
                    }
                    if (connectionString == null || connectionString.isBlank()) {
                        throw new IllegalStateException("Connection string do Azure Storage não configurada. Defina AzureWebJobsStorage ou AZURE_STORAGE_CONNECTION_STRING.");
                    }
                    tableServiceClient = new TableServiceClientBuilder()
                            .connectionString(connectionString)
                            .buildClient();
                    LOG.info("Conexão estabelecida com sucesso");
                }
            }
        }
    }

    /**
     * Salva uma avaliação no Azure Storage Tables
     */
    public void salvarAvaliacao(Avaliacao avaliacao) {
        try {
            initialize();
            LOG.infof("Salvando avaliação: %s", avaliacao.getId());

            // Cria a tabela se não existir
            try {
                tableServiceClient.createTableIfNotExists(TABLE_AVALIACOES);
            } catch (Exception e) {
                LOG.warnf("Tabela %s pode já existir ou erro ao criar: %s", TABLE_AVALIACOES, e.getMessage());
            }

            TableClient tableClient = tableServiceClient.getTableClient(TABLE_AVALIACOES);
            TableEntity entity = new TableEntity(PARTITION_KEY_AVALIACOES, avaliacao.getId())
                    .addProperty("descricao", avaliacao.getDescricao())
                    .addProperty("nota", avaliacao.getNota())
                    .addProperty("urgencia", avaliacao.getUrgencia() != null ? avaliacao.getUrgencia().toString() : "NORMAL")
                    .addProperty("dataHora", avaliacao.getDataHora() != null ? avaliacao.getDataHora().toString() : LocalDateTime.now().toString());

            tableClient.createEntity(entity);
            LOG.infof("Avaliação salva com sucesso: %s", avaliacao.getId());
        } catch (Exception e) {
            LOG.errorf("Erro ao salvar avaliação: %s", e.getMessage());
            throw new RuntimeException("Erro ao salvar avaliação", e);
        }
    }

    /**
     * Busca avaliações em um período específico
     */
    public List<Avaliacao> buscarAvaliacoesPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        try {
            initialize();
            LOG.infof("Buscando avaliações entre %s e %s", inicio, fim);

            TableClient tableClient = tableServiceClient.getTableClient(TABLE_AVALIACOES);

            String filter = String.format("Timestamp ge datetime'%s' and Timestamp lt datetime'%s'",
                    inicio.format(DateTimeFormatter.ISO_DATE_TIME),
                    fim.format(DateTimeFormatter.ISO_DATE_TIME));

            ListEntitiesOptions options = new ListEntitiesOptions().setFilter(filter);

            List<Avaliacao> avaliacoes = tableClient.listEntities(options, null, null)
                    .stream()
                    .map(this::fromTableEntity)
                    .collect(Collectors.toList());

            LOG.infof("Total de avaliações encontradas: %d", avaliacoes.size());
            return avaliacoes;
        } catch (Exception e) {
            LOG.errorf("Erro ao buscar avaliações: %s", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Salva um relatório semanal no Azure Storage Tables
     */
    public void salvarRelatorio(RelatorioSemanal relatorio) {
        try {
            initialize();
            LOG.infof("Salvando relatório: %s", relatorio.getId());


            // Cria a tabela se não existir
            try {
                tableServiceClient.createTableIfNotExists(TABLE_RELATORIOS);
            } catch (Exception e) {
                LOG.warnf("Tabela %s pode já existir ou erro ao criar: %s", TABLE_RELATORIOS, e.getMessage());
            }

            TableClient tableClient = tableServiceClient.getTableClient(TABLE_RELATORIOS);
            TableEntity entity = new TableEntity(PARTITION_KEY_RELATORIOS, relatorio.getId())
                    .addProperty("dataGeracao", relatorio.getDataGeracao() != null ? relatorio.getDataGeracao().toString() : LocalDateTime.now().toString())
                    .addProperty("periodoInicio", relatorio.getPeriodoInicio() != null ? relatorio.getPeriodoInicio().toString() : "")
                    .addProperty("periodoFim", relatorio.getPeriodoFim() != null ? relatorio.getPeriodoFim().toString() : "")
                    .addProperty("totalAvaliacoes", relatorio.getTotalAvaliacoes())
                    .addProperty("mediaNotas", relatorio.getMediaNotas())
                    .addProperty("notaMaisAlta", relatorio.getNotaMaisAlta() != null ? relatorio.getNotaMaisAlta() : 0)
                    .addProperty("notaMaisBaixa", relatorio.getNotaMaisBaixa() != null ? relatorio.getNotaMaisBaixa() : 0);

            if (relatorio.getAvaliacoesPorDia() != null) {
                StringBuilder diasJson = new StringBuilder();
                relatorio.getAvaliacoesPorDia().forEach((dia, count) -> {
                    if (diasJson.length() > 0) diasJson.append(",");
                    diasJson.append(String.format("\"%s\":%d", dia, count));
                });
                entity.addProperty("avaliacoesPorDia", "{" + diasJson + "}");
            }

            // Adiciona contagem por urgência como propriedades separadas
            if (relatorio.getContagemPorUrgencia() != null) {
                relatorio.getContagemPorUrgencia().forEach((nivel, count) -> {
                    entity.addProperty("contagem_" + nivel, count);
                });
            }

            // Adiciona palavras recorrentes (serializa como JSON string)
            if (relatorio.getPalavrasRecorrentes() != null && !relatorio.getPalavrasRecorrentes().isEmpty()) {
                StringBuilder palavrasJson = new StringBuilder();
                for (java.util.Map.Entry<String, Long> entry : relatorio.getPalavrasRecorrentes().entrySet()) {
                    if (palavrasJson.length() > 0) palavrasJson.append(",");
                    String palavra = entry.getKey() != null ? entry.getKey() : "";
                    Long count = entry.getValue() != null ? entry.getValue() : 0L;
                    palavrasJson.append(String.format("\"%s\":%d", palavra.replace("\"", "\\\\\""), count.longValue()));
                }
                entity.addProperty("palavrasRecorrentes", "{" + palavrasJson + "}");
            }

            // Adiciona frases recorrentes (serializa como JSON string)
            if (relatorio.getFrasesRecorrentes() != null && !relatorio.getFrasesRecorrentes().isEmpty()) {
                StringBuilder frasesJson = new StringBuilder();
                for (java.util.Map.Entry<String, Long> entry : relatorio.getFrasesRecorrentes().entrySet()) {
                    if (frasesJson.length() > 0) frasesJson.append(",");
                    String frase = entry.getKey() != null ? entry.getKey() : "";
                    Long count = entry.getValue() != null ? entry.getValue() : 0L;
                    frasesJson.append(String.format("\"%s\":%d", frase.replace("\"", "\\\\\""), count.longValue()));
                }
                entity.addProperty("frasesRecorrentes", "{" + frasesJson + "}");
            }

            tableClient.createEntity(entity);
            LOG.infof("Relatório salvo com sucesso: %s", relatorio.getId());
        } catch (Exception e) {
            LOG.errorf("Erro ao salvar relatório: %s", e.getMessage());
            throw new RuntimeException("Erro ao salvar relatório", e);
        }
    }

    /**
     * Converte TableEntity para Avaliacao
     */
    private Avaliacao fromTableEntity(TableEntity entity) {
        String descricao = (String) entity.getProperty("descricao");
        Integer nota = (Integer) entity.getProperty("nota");
        String urgenciaStr = (String) entity.getProperty("urgencia");
        String dataHoraStr = (String) entity.getProperty("dataHora");

        Avaliacao.NivelUrgencia urgencia = Avaliacao.NivelUrgencia.NORMAL;
        if (urgenciaStr != null) {
            try {
                urgencia = Avaliacao.NivelUrgencia.valueOf(urgenciaStr);
            } catch (IllegalArgumentException e) {
                LOG.warnf("Urgência inválida: %s", urgenciaStr);
            }
        }

        LocalDateTime dataHora = null;
        if (dataHoraStr != null) {
            try {
                dataHora = LocalDateTime.parse(dataHoraStr);
            } catch (Exception e) {
                LOG.warnf("Data inválida: %s", dataHoraStr);
                dataHora = LocalDateTime.now();
            }
        } else {
            dataHora = LocalDateTime.now();
        }

        return Avaliacao.builder()
                .id(entity.getRowKey())
                .descricao(descricao != null ? descricao : "")
                .nota(nota != null ? nota : 0)
                .urgencia(urgencia)
                .dataHora(dataHora)
                .build();
    }

    /**
     * Fecha a conexão com o Azure Storage Tables
     */
    public void close() {
        // TableServiceClient não precisa de close explícito
    }
}
