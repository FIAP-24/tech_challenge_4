package br.com.fiap.techchallenge.service;

import br.com.fiap.techchallenge.model.Avaliacao;
import br.com.fiap.techchallenge.model.RelatorioSemanal;
import br.com.fiap.techchallenge.repository.StorageTableRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serviço para geração de relatórios
 * Calcula estatísticas e métricas das avaliações
 */
@ApplicationScoped
@RequiredArgsConstructor
public class RelatorioService {

    private static final Logger LOG = Logger.getLogger(RelatorioService.class);

    private final StorageTableRepository repository;
    private final AnaliseTextoService analiseTextoService;

    /**
     * Gera relatório semanal com estatísticas das avaliações
     */
    public RelatorioSemanal gerarRelatorioSemanal() {
        LOG.info("Iniciando geração de relatório semanal");

        // Define o período (últimos 7 dias)
        LocalDateTime fim = LocalDateTime.now();
        LocalDateTime inicio = fim.minusDays(7);

        // Busca avaliações do período
        List<Avaliacao> avaliacoes = repository.buscarAvaliacoesPorPeriodo(inicio, fim);
        LOG.infof("Total de avaliações no período: %d", avaliacoes.size());

        // Se não houver avaliações, retorna relatório vazio
        if (avaliacoes.isEmpty()) {
            LOG.warn("Nenhuma avaliação encontrada no período");
            return criarRelatorioVazio(inicio, fim);
        }

        // Calcula métricas
        long total = avaliacoes.size();
        double media = avaliacoes.stream()
                .mapToInt(Avaliacao::getNota)
                .average()
                .orElse(0.0);

        Integer notaMaisAlta = avaliacoes.stream()
                .map(Avaliacao::getNota)
                .max(Comparator.naturalOrder())
                .orElse(0);

        Integer notaMaisBaixa = avaliacoes.stream()
                .map(Avaliacao::getNota)
                .min(Comparator.naturalOrder())
                .orElse(0);

        // Contagem por urgência
        Map<String, Long> contagemPorUrgencia = avaliacoes.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getUrgencia().toString(),
                        Collectors.counting()
                ));

        // Análise de comentários recorrentes
        List<String> descricoes = avaliacoes.stream()
                .map(Avaliacao::getDescricao)
                .filter(desc -> desc != null && !desc.trim().isEmpty())
                .collect(Collectors.toList());

        Map<String, Long> palavrasRecorrentes = analiseTextoService.analisarPalavrasRecorrentes(descricoes);
        Map<String, Long> frasesRecorrentes = analiseTextoService.analisarFrasesRecorrentes(descricoes);

        LOG.infof("Análise de texto concluída - %d palavras e %d frases recorrentes identificadas",
                palavrasRecorrentes.size(), frasesRecorrentes.size());

        // Cria relatório
        RelatorioSemanal relatorio = RelatorioSemanal.builder()
                .periodoInicio(inicio)
                .periodoFim(fim)
                .totalAvaliacoes(total)
                .mediaNotas(media)
                .notaMaisAlta(notaMaisAlta)
                .notaMaisBaixa(notaMaisBaixa)
                .contagemPorUrgencia(contagemPorUrgencia)
                .palavrasRecorrentes(palavrasRecorrentes)
                .frasesRecorrentes(frasesRecorrentes)
                .build();

        relatorio.inicializar();

        LOG.infof("Relatório gerado - Média: %.2f, Total: %d", media, total);
        return relatorio;
    }

    /**
     * Cria relatório vazio para períodos sem avaliações
     */
    private RelatorioSemanal criarRelatorioVazio(LocalDateTime inicio, LocalDateTime fim) {
        RelatorioSemanal relatorio = RelatorioSemanal.builder()
                .periodoInicio(inicio)
                .periodoFim(fim)
                .totalAvaliacoes(0)
                .mediaNotas(0.0)
                .notaMaisAlta(0)
                .notaMaisBaixa(0)
                .contagemPorUrgencia(Map.of())
                .palavrasRecorrentes(Map.of())
                .frasesRecorrentes(Map.of())
                .build();

        relatorio.inicializar();
        return relatorio;
    }
}
