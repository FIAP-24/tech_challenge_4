package br.com.fiap.techchallenge.functions;

import br.com.fiap.techchallenge.model.RelatorioSemanal;
import br.com.fiap.techchallenge.repository.StorageTableRepository;
import br.com.fiap.techchallenge.service.EmailService;
import br.com.fiap.techchallenge.service.RelatorioService;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Azure Function com Timer Trigger para geração de relatório semanal
 * Executado toda segunda-feira às 9h (0 0 9 * * MON)
 * <p>
 * Responsabilidades:
 * - Buscar avaliações dos últimos 7 dias
 * - Calcular métricas (média, contagens por urgência)
 * - Persistir relatório no Azure Storage Tables
 * - Enviar e-mail resumo para administradores
 * - Registrar telemetria
 */
@ApplicationScoped
public class RelatorioFunction {

    private static final Logger LOG = Logger.getLogger(RelatorioFunction.class);

    private final RelatorioService relatorioService;
    private final StorageTableRepository repository;
    private final EmailService emailService;
    //private final TelemetryClient telemetryClient = new TelemetryClient();

    public RelatorioFunction(RelatorioService relatorioService, StorageTableRepository repository, EmailService emailService) {
        this.relatorioService = relatorioService;
        this.repository = repository;
        this.emailService = emailService;
    }

    /**
     * Timer Trigger: Executa toda segunda-feira às 9h
     * Cron expression: 0 0 9 * * MON
     * <p>
     * Formato: {segundo} {minuto} {hora} {dia} {mês} {dia-da-semana}
     */
    @FunctionName("RelatorioSemanalHandler")
    public void run(
            @TimerTrigger(
                    name = "timer",
                    schedule = "0 0 9 * * MON",
                    dataType = "string"
            ) String timerInfo,
            final ExecutionContext context) {

        TelemetryClient telemetryClient = new TelemetryClient();

        LOG.info("=== Iniciando geração de relatório semanal ===");
        LOG.infof("Timer Info: %s", timerInfo);

        try {
            // 1. Gera relatório com métricas
            RelatorioSemanal relatorio = relatorioService.gerarRelatorioSemanal();
            LOG.infof("Relatório gerado - ID: %s", relatorio.getId());
            LOG.infof("Total avaliações: %d, Média: %.2f",
                    relatorio.getTotalAvaliacoes(),
                    relatorio.getMediaNotas());

            // 2. Persiste relatório no Azure Storage Tables
            repository.salvarRelatorio(relatorio);
            LOG.info("Relatório persistido com sucesso");

            // 3. Envia e-mail resumo para administradores
            emailService.enviarRelatorioSemanal(relatorio);
            LOG.info("E-mail de relatório enviado");

            // 4. Registra telemetria
            telemetryClient.trackEvent("RelatorioSemanalGerado");
            telemetryClient.trackMetric("TotalAvaliacoesSemanal", relatorio.getTotalAvaliacoes());
            telemetryClient.trackMetric("MediaNotasSemanal", relatorio.getMediaNotas());

            LOG.info("=== Relatório semanal processado com sucesso ===");

        } catch (Exception e) {
            LOG.errorf("Erro ao gerar relatório semanal: %s", e.getMessage());
            telemetryClient.trackException(e);
            throw new RuntimeException("Falha na geração do relatório semanal", e);
        }
    }
}
