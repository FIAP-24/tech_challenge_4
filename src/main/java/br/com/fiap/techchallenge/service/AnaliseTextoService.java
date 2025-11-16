package br.com.fiap.techchallenge.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço para análise de texto e identificação de comentários recorrentes
 * Processa descrições de avaliações para encontrar palavras e frases mais frequentes
 */
@ApplicationScoped
public class AnaliseTextoService {

    private static final Logger LOG = Logger.getLogger(AnaliseTextoService.class);
    
    // Palavras comuns em português que devem ser ignoradas (stop words)
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "o", "e", "de", "do", "da", "em", "um", "uma", "para", "com", "não",
            "é", "que", "se", "na", "por", "mais", "as", "os", "como", "mas", "foi",
            "ao", "ele", "das", "tem", "à", "seu", "sua", "ou", "ser", "quando",
            "muito", "há", "nos", "já", "está", "eu", "também", "só", "pelo", "pela",
            "até", "isso", "ela", "entre", "era", "depois", "sem", "mesmo", "aos",
            "ter", "seus", "suas", "numa", "pelos", "pelas", "num", "nem", "suas",
            "meu", "às", "minha", "têm", "numa", "pelos", "pelas", "num", "nem",
            "meu", "às", "minha", "têm", "pelos", "pelas", "num", "nem"
    );

    // Tamanho mínimo de palavra para ser considerada
    private static final int MIN_PALAVRA_LENGTH = 3;
    
    // Número máximo de palavras/frases mais recorrentes a retornar
    private static final int MAX_RESULTADOS = 10;

    /**
     * Analisa uma lista de descrições e retorna as palavras mais recorrentes
     * 
     * @param descricoes Lista de descrições das avaliações
     * @return Map com palavra e sua frequência, ordenado por frequência decrescente
     */
    public Map<String, Long> analisarPalavrasRecorrentes(List<String> descricoes) {
        if (descricoes == null || descricoes.isEmpty()) {
            LOG.warn("Lista de descrições vazia para análise");
            return new LinkedHashMap<>();
        }

        LOG.infof("Analisando %d descrições para identificar palavras recorrentes", descricoes.size());

        // Processa todas as descrições
        Map<String, Long> frequenciaPalavras = descricoes.stream()
                .filter(Objects::nonNull)
                .filter(desc -> !desc.trim().isEmpty())
                .flatMap(desc -> extrairPalavras(desc).stream())
                .filter(palavra -> palavra.length() >= MIN_PALAVRA_LENGTH)
                .filter(palavra -> !STOP_WORDS.contains(palavra.toLowerCase()))
                .map(String::toLowerCase)
                .collect(Collectors.groupingBy(
                        palavra -> palavra,
                        Collectors.counting()
                ));

        // Ordena por frequência e limita aos top N
        Map<String, Long> palavrasRecorrentes = frequenciaPalavras.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_RESULTADOS)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        LOG.infof("Identificadas %d palavras recorrentes", palavrasRecorrentes.size());
        return palavrasRecorrentes;
    }

    /**
     * Analisa uma lista de descrições e retorna as frases mais recorrentes
     * Considera frases de 2 a 3 palavras
     * 
     * @param descricoes Lista de descrições das avaliações
     * @return Map com frase e sua frequência, ordenado por frequência decrescente
     */
    public Map<String, Long> analisarFrasesRecorrentes(List<String> descricoes) {
        if (descricoes == null || descricoes.isEmpty()) {
            LOG.warn("Lista de descrições vazia para análise de frases");
            return new LinkedHashMap<>();
        }

        LOG.infof("Analisando %d descrições para identificar frases recorrentes", descricoes.size());

        // Extrai frases de 2 e 3 palavras
        Map<String, Long> frequenciaFrases = descricoes.stream()
                .filter(Objects::nonNull)
                .filter(desc -> !desc.trim().isEmpty())
                .flatMap(desc -> extrairFrases(desc).stream())
                .filter(frase -> !frase.trim().isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.groupingBy(
                        frase -> frase,
                        Collectors.counting()
                ));

        // Filtra frases que aparecem pelo menos 2 vezes e ordena por frequência
        Map<String, Long> frasesRecorrentes = frequenciaFrases.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2) // Mínimo 2 ocorrências
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_RESULTADOS)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        LOG.infof("Identificadas %d frases recorrentes", frasesRecorrentes.size());
        return frasesRecorrentes;
    }

    /**
     * Extrai palavras de uma descrição, removendo pontuação e normalizando
     */
    private List<String> extrairPalavras(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Remove pontuação e caracteres especiais, mantém apenas letras e espaços
        String textoLimpo = texto.replaceAll("[^\\p{L}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return Arrays.stream(textoLimpo.split("\\s+"))
                .filter(palavra -> !palavra.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Extrai frases (bigramas e trigramas) de uma descrição
     */
    private List<String> extrairFrases(String texto) {
        List<String> palavras = extrairPalavras(texto);
        List<String> frases = new ArrayList<>();

        if (palavras.size() < 2) {
            return frases;
        }

        // Bigramas (2 palavras)
        for (int i = 0; i < palavras.size() - 1; i++) {
            String palavra1 = palavras.get(i).toLowerCase();
            String palavra2 = palavras.get(i + 1).toLowerCase();
            
            // Ignora se contém stop words
            if (!STOP_WORDS.contains(palavra1) && !STOP_WORDS.contains(palavra2)) {
                frases.add(palavra1 + " " + palavra2);
            }
        }

        // Trigramas (3 palavras)
        if (palavras.size() >= 3) {
            for (int i = 0; i < palavras.size() - 2; i++) {
                String palavra1 = palavras.get(i).toLowerCase();
                String palavra2 = palavras.get(i + 1).toLowerCase();
                String palavra3 = palavras.get(i + 2).toLowerCase();
                
                // Ignora se todas são stop words
                if (!STOP_WORDS.contains(palavra1) || 
                    !STOP_WORDS.contains(palavra2) || 
                    !STOP_WORDS.contains(palavra3)) {
                    frases.add(palavra1 + " " + palavra2 + " " + palavra3);
                }
            }
        }

        return frases;
    }
}

