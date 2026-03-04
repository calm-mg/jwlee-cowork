package io.autocrypt.jwlee.cowork.service;

import com.embabel.agent.api.annotation.LlmTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Component
public class CatFactService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LlmTool(description = "Fetch random interesting facts about cats from an external API. Use this to get actual cat trivia.")
    public List<String> getCatFacts(int count) {
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://meowfacts.herokuapp.com/?count=" + count))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode dataNode = root.get("data");
            
            List<String> facts = new ArrayList<>();
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode node : dataNode) {
                    facts.add(node.asText());
                }
            }
            return facts;
        } catch (Exception e) {
            return List.of("Failed to fetch cat facts: " + e.getMessage());
        }
    }
}
