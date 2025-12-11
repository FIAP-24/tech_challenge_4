package br.com.fiap.techchallenge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de dados para Avaliação
 * Representa uma avaliação de feedback com descrição e nota
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Avaliacao {

    @JsonProperty("id")
    private String id;

    @NotBlank(message = "Descrição é obrigatória")
    @JsonProperty("descricao")
    private String descricao;

    @NotNull(message = "Nota é obrigatória")
    @Min(value = 0, message = "Nota mínima é 0")
    @Max(value = 10, message = "Nota máxima é 10")
    @JsonProperty("nota")
    private Integer nota;

    @JsonProperty("urgencia")
    private NivelUrgencia urgencia;

    @JsonProperty("dataHora")
    private LocalDateTime dataHora;

    /**
     * Inicializa a avaliação com ID único e data/hora atual
     */
    public void inicializar() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.dataHora == null) {
            this.dataHora = LocalDateTime.now();
        }
    }

    /**
     * Calcula o nível de urgência baseado na nota
     * Crítico: nota <= 3
     * Alto: nota 4-6
     * Normal: nota >= 7
     */
    public void calcularUrgencia(int threshold) {
        if (nota <= threshold) {
            this.urgencia = NivelUrgencia.CRITICO;
        } else if (nota <= 6) {
            this.urgencia = NivelUrgencia.ALTO;
        } else {
            this.urgencia = NivelUrgencia.NORMAL;
        }
    }

    public enum NivelUrgencia {
        CRITICO,
        ALTO,
        NORMAL
    }
}
