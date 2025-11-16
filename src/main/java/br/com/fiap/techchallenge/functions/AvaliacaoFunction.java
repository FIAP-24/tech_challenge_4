package br.com.fiap.techchallenge.functions;

import br.com.fiap.techchallenge.model.Avaliacao;
import br.com.fiap.techchallenge.repository.StorageTableRepository;
import br.com.fiap.techchallenge.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Azure Function para receber e processar avaliações
 * Endpoint: POST /api/avaliacao
 *
 * Responsabilidades:
 * - Validar dados de entrada
 * - Calcular nível de urgência
 * - Persistir no Azure Storage Tables
 * - Enviar notificação se crítico
 * - Registrar telemetria
 */
public class AvaliacaoFunction {

    private static final Logger LOG = Logger.getLogger(AvaliacaoFunction.class);
    private static final int URGENCIA_CRITICA_THRESHOLD = 3;

    private final StorageTableRepository repository;
    private final EmailService emailService;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final TelemetryClient telemetryClient;
    
    public AvaliacaoFunction(StorageTableRepository repository, EmailService emailService, Validator validator) {
        this.repository = repository;
        this.emailService = emailService;
        this.validator = validator;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.telemetryClient = new TelemetryClient();
    }

    /**
     * HTTP POST /api/avaliacao
     * Recebe uma avaliação em formato JSON
     *
     * Payload esperado:
     * {
     *   "descricao": "string (obrigatório)",
     *   "nota": integer 0-10 (obrigatório)
     * }
     */
    @FunctionName("AvaliacaoHandler")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "avaliacao"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        LOG.info("=== Iniciando processamento de avaliação ===");

        try {
            // 1. Parse do JSON
            String body = request.getBody().orElse(null);
            if (body == null || body.isBlank()) {
                LOG.warn("Body vazio recebido");
                return criarRespostaErro(request, 400, "Body da requisição é obrigatório");
            }

            Avaliacao avaliacao = objectMapper.readValue(body, Avaliacao.class);
            LOG.infof("Avaliação parseada - Nota: %d", avaliacao.getNota());

            // 2. Validação
            Set<ConstraintViolation<Avaliacao>> violations = validator.validate(avaliacao);
            if (!violations.isEmpty()) {
                String erros = violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining(", "));
                LOG.warnf("Validação falhou: %s", erros);
                return criarRespostaErro(request, 400, "Erro de validação: " + erros);
            }

            // 3. Inicializa e calcula urgência
            avaliacao.inicializar();
            avaliacao.calcularUrgencia(URGENCIA_CRITICA_THRESHOLD);
            LOG.infof("Urgência calculada: %s", avaliacao.getUrgencia());

            // 4. Persiste no Azure Storage Tables
            repository.salvarAvaliacao(avaliacao);
            LOG.info("Avaliação persistida com sucesso");

            // 5. Verifica se é crítico e envia notificação
            if (avaliacao.getUrgencia() == Avaliacao.NivelUrgencia.CRITICO) {
                LOG.warn("Avaliação CRÍTICA detectada - enviando notificação");
                emailService.enviarNotificacaoCritica(avaliacao);
                telemetryClient.trackEvent("AvaliacaoCritica");
            }

            // 6. Registra métrica
            telemetryClient.trackEvent("AvaliacaoRecebida");
            telemetryClient.trackMetric("NotaAvaliacao", avaliacao.getNota());

            LOG.info("=== Avaliação processada com sucesso ===");

            // 7. Retorna resposta de sucesso
            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body(avaliacao)
                    .build();

        } catch (Exception e) {
            LOG.errorf("Erro ao processar avaliação: %s", e.getMessage());
            telemetryClient.trackException(e);
            return criarRespostaErro(request, 500, "Erro interno: " + e.getMessage());
        }
    }

    /**
     * Cria resposta de erro padronizada
     */
    private HttpResponseMessage criarRespostaErro(HttpRequestMessage<?> request, int status, String mensagem) {
        String json = String.format("{\"erro\": \"%s\"}", mensagem);
        return request.createResponseBuilder(HttpStatus.valueOf(status))
                .header("Content-Type", "application/json")
                .body(json)
                .build();
    }
}
