package br.com.fiap.techchallenge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Modelo de dados para Relatório Semanal
 * Contém estatísticas agregadas das avaliações
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatorioSemanal {

    @JsonProperty("id")
    private String id;

    @JsonProperty("dataGeracao")
    private LocalDateTime dataGeracao;

    @JsonProperty("periodoInicio")
    private LocalDateTime periodoInicio;

    @JsonProperty("periodoFim")
    private LocalDateTime periodoFim;

    @JsonProperty("totalAvaliacoes")
    private long totalAvaliacoes;

    @JsonProperty("mediaNotas")
    private double mediaNotas;

    @JsonProperty("contagemPorUrgencia")
    private Map<String, Long> contagemPorUrgencia;

    @JsonProperty("notaMaisAlta")
    private Integer notaMaisAlta;

    @JsonProperty("notaMaisBaixa")
    private Integer notaMaisBaixa;

    @JsonProperty("palavrasRecorrentes")
    private Map<String, Long> palavrasRecorrentes;

    @JsonProperty("frasesRecorrentes")
    private Map<String, Long> frasesRecorrentes;

    @JsonProperty("avaliacoesPorDia")
    private Map<String, Long> avaliacoesPorDia;

    public Map<String, Long> getAvaliacoesPorUrgencia() {
        return this.contagemPorUrgencia; // Mapeia para o campo existente
    }

    public List<String> getPalavrasMaisRecorrentes() {
        return new ArrayList<>(this.palavrasRecorrentes.keySet()); // Converte o Map para List
    }

    public List<String> getFrasesRecorrentes() {
        return new ArrayList<>(this.frasesRecorrentes.keySet()); // Converte o Map para List
    }
    /**
     * Inicializa o relatório com ID único e data de geração
     */
    public void inicializar() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.dataGeracao == null) {
            this.dataGeracao = LocalDateTime.now();
        }
        if (this.palavrasRecorrentes == null) {
            this.palavrasRecorrentes = Map.of();
        }
        if (this.frasesRecorrentes == null) {
            this.frasesRecorrentes = Map.of();
        }
        if (this.avaliacoesPorDia == null) {
            this.avaliacoesPorDia = Map.of();
        }
    }
}
