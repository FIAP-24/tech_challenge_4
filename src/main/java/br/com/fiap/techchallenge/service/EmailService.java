package br.com.fiap.techchallenge.service;

import br.com.fiap.techchallenge.model.Avaliacao;
import br.com.fiap.techchallenge.model.RelatorioSemanal;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;

/**
 * Serviço para envio de e-mails via SendGrid
 * Gerencia notificações de avaliações críticas e relatórios semanais
 */
@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);

    @ConfigProperty(name = "sendgrid.api.key")
    String sendGridApiKey;

    @ConfigProperty(name = "sendgrid.from.email")
    String fromEmail;

    @ConfigProperty(name = "sendgrid.admin.email")
    String adminEmail;

    /**
     * Envia notificação de avaliação crítica para administradores
     */
    public void enviarNotificacaoCritica(Avaliacao avaliacao) {
        try {
            LOG.infof("Enviando notificação crítica para: %s", adminEmail);

            String subject = "⚠️ URGENTE: Nova Avaliação Crítica Recebida";
            String body = construirEmailCritico(avaliacao);

            enviarEmail(adminEmail, subject, body);

            LOG.info("Notificação crítica enviada com sucesso");
        } catch (Exception e) {
            LOG.errorf("Erro ao enviar notificação crítica: %s", e.getMessage());
            // Não lança exceção para não bloquear o fluxo principal
        }
    }

    /**
     * Envia relatório semanal para administradores
     */
    public void enviarRelatorioSemanal(RelatorioSemanal relatorio) {
        try {
            LOG.infof("Enviando relatório semanal para: %s", adminEmail);

            String subject = "📊 Relatório Semanal de Feedback";
            String body = construirEmailRelatorio(relatorio);

            enviarEmail(adminEmail, subject, body);

            LOG.info("Relatório semanal enviado com sucesso");
        } catch (Exception e) {
            LOG.errorf("Erro ao enviar relatório semanal: %s", e.getMessage());
            // Não lança exceção para não bloquear o fluxo principal
        }
    }

    /**
     * Método genérico para enviar e-mail via SendGrid
     */
    private void enviarEmail(String toEmail, String subject, String body) throws IOException {
        Email from = new Email(fromEmail);
        Email to = new Email(toEmail);
        Content content = new Content("text/html", body);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);
            LOG.infof("SendGrid Response - Status: %d", response.getStatusCode());

            if (response.getStatusCode() >= 400) {
                LOG.errorf("Erro no SendGrid: %s", response.getBody());
            }
        } catch (IOException e) {
            LOG.errorf("Erro ao enviar e-mail: %s", e.getMessage());
            throw e;
        }
    }

    /**
     * Constrói o corpo do e-mail para avaliação crítica
     */
    private String construirEmailCritico(Avaliacao avaliacao) {
        return String.format("""
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <style>
                                body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                                .header { background-color: #dc3545; color: white; padding: 20px; border-radius: 5px; }
                                .content { background-color: #f8f9fa; padding: 20px; margin-top: 20px; border-radius: 5px; }
                                .info { margin: 10px 0; }
                                .label { font-weight: bold; }
                                .urgencia { color: #dc3545; font-weight: bold; font-size: 18px; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1>⚠️ Avaliação Crítica Recebida</h1>
                                </div>
                                <div class="content">
                                    <p>Uma nova avaliação com <span class="urgencia">urgência CRÍTICA</span> foi registrada no sistema.</p>
                        
                                    <div class="info">
                                        <span class="label">ID:</span> %s
                                    </div>
                                    <div class="info">
                                        <span class="label">Data/Hora:</span> %s
                                    </div>
                                    <div class="info">
                                        <span class="label">Nota:</span> %d/10
                                    </div>
                                    <div class="info">
                                        <span class="label">Urgência:</span> %s
                                    </div>
                                    <div class="info">
                                        <span class="label">Descrição:</span>
                                        <p style="background-color: white; padding: 15px; border-left: 4px solid #dc3545; margin-top: 10px;">
                                            %s
                                        </p>
                                    </div>
                        
                                    <p style="margin-top: 20px; color: #666;">
                                        <strong>Ação Recomendada:</strong> Esta avaliação requer atenção imediata.
                                        Por favor, entre em contato com o cliente o mais breve possível.
                                    </p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                avaliacao.getId(),
                avaliacao.getDataHora(),
                avaliacao.getNota(),
                avaliacao.getUrgencia(),
                avaliacao.getDescricao()
        );
    }

    /**
     * Constrói o corpo do e-mail para relatório semanal
     */
    private String construirEmailRelatorio(RelatorioSemanal relatorio) {
        StringBuilder urgencias = new StringBuilder();
        relatorio.getContagemPorUrgencia().forEach((nivel, count) -> {
            urgencias.append(String.format(
                    "<div class='info'><span class='label'>%s:</span> %d avaliações</div>%n",
                    nivel, count
            ));
        });

        // Palavras recorrentes
        StringBuilder palavrasHtml = new StringBuilder();
        if (relatorio.getPalavrasRecorrentes() != null && !relatorio.getPalavrasRecorrentes().isEmpty()) {
            relatorio.getPalavrasRecorrentes().forEach((palavra, count) -> {
                palavrasHtml.append(String.format(
                        "<div class='info'><span class='label'>\"%s\":</span> %d ocorrências</div>%n",
                        palavra, count
                ));
            });
        } else {
            palavrasHtml.append("<div class='info' style='color: #666;'>Nenhuma palavra recorrente identificada</div>");
        }

        // Frases recorrentes
        StringBuilder frasesHtml = new StringBuilder();
        if (relatorio.getFrasesRecorrentes() != null && !relatorio.getFrasesRecorrentes().isEmpty()) {
            relatorio.getFrasesRecorrentes().forEach((frase, count) -> {
                frasesHtml.append(String.format(
                        "<div class='info'><span class='label'>\"%s\":</span> %d ocorrências</div>%n",
                        frase, count
                ));
            });
        } else {
            frasesHtml.append("<div class='info' style='color: #666;'>Nenhuma frase recorrente identificada</div>");
        }

        return String.format("""
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <style>
                                body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                                .header { background-color: #007bff; color: white; padding: 20px; border-radius: 5px; }
                                .content { background-color: #f8f9fa; padding: 20px; margin-top: 20px; border-radius: 5px; }
                                .info { margin: 10px 0; }
                                .label { font-weight: bold; }
                                .metric { background-color: white; padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 4px solid #007bff; }
                                .metric-value { font-size: 24px; font-weight: bold; color: #007bff; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1>📊 Relatório Semanal de Feedback</h1>
                                </div>
                                <div class="content">
                                    <p><strong>Período:</strong> %s a %s</p>
                                    <p><strong>Data de Geração:</strong> %s</p>
                        
                                    <h2>Métricas Gerais</h2>
                        
                                    <div class="metric">
                                        <div class="label">Total de Avaliações</div>
                                        <div class="metric-value">%d</div>
                                    </div>
                        
                                    <div class="metric">
                                        <div class="label">Média das Notas</div>
                                        <div class="metric-value">%.2f / 10</div>
                                    </div>
                        
                                    <div class="metric">
                                        <div class="label">Nota Mais Alta</div>
                                        <div class="metric-value">%d</div>
                                    </div>
                        
                                    <div class="metric">
                                        <div class="label">Nota Mais Baixa</div>
                                        <div class="metric-value">%d</div>
                                    </div>
                        
                                    <h2>Distribuição por Urgência</h2>
                                    <div style="background-color: white; padding: 15px; border-radius: 5px;">
                                        %s
                                    </div>
                        
                                    <h2>Palavras Mais Recorrentes</h2>
                                    <div style="background-color: white; padding: 15px; border-radius: 5px; margin-top: 10px;">
                                        %s
                                    </div>
                        
                                    <h2>Frases Mais Recorrentes</h2>
                                    <div style="background-color: white; padding: 15px; border-radius: 5px; margin-top: 10px;">
                                        %s
                                    </div>
                        
                                    <p style="margin-top: 20px; color: #666;">
                                        Este relatório é gerado automaticamente toda semana.
                                        Para mais detalhes, acesse o portal de administração.
                                    </p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                relatorio.getPeriodoInicio(),
                relatorio.getPeriodoFim(),
                relatorio.getDataGeracao(),
                relatorio.getTotalAvaliacoes(),
                relatorio.getMediaNotas(),
                relatorio.getNotaMaisAlta(),
                relatorio.getNotaMaisBaixa(),
                urgencias,
                palavrasHtml,
                frasesHtml
        );
    }
}
