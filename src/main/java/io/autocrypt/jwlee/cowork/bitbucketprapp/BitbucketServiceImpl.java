package io.autocrypt.jwlee.cowork.bitbucketprapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Map;

@Service
public class BitbucketServiceImpl implements BitbucketService {

    private static final Logger log = LoggerFactory.getLogger(BitbucketServiceImpl.class);

    private final RestClient restClient;

    public BitbucketServiceImpl(
            @Value("${bitbucket.api.base-url:https://api.bitbucket.org/2.0}") String baseUrl,
            @Value("${BITBUCKET_API_TOKEN:${ATLASSIAN_API_TOKEN:}}") String apiToken) {
        
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .build();
    }

    @Override
    public PullRequestData fetchPullRequest(String workspace, String repository, String prId) {
        log.info("Fetching PR {} from Bitbucket repository {}/{}", prId, workspace, repository);

        try {
            Map<?, ?> prData = restClient.get()
                    .uri("/repositories/{workspace}/{repo}/pullrequests/{id}", workspace, repository, prId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (prData == null) {
                throw new IllegalStateException("Bitbucket PR response body is empty");
            }

            String title = extractTitle(prData);
            String description = extractDescription(prData);

            log.info("Fetching Diff for PR {}", prId);
            String rawDiff = restClient.get()
                    .uri("/repositories/{workspace}/{repo}/pullrequests/{id}/diff", workspace, repository, prId)
                    .accept(MediaType.TEXT_PLAIN)
                    .retrieve()
                    .body(String.class);

            return new PullRequestData(prId, title, description, rawDiff);
        } catch (Exception e) {
            log.error("Failed to fetch PR {}: {}", prId, e.getMessage());
            throw new RuntimeException("Bitbucket API failed: " + e.getMessage(), e);
        }
    }

    static String extractTitle(Map<?, ?> prData) {
        return firstNonBlank(
                stringValue(prData.get("title")),
                nestedString(prData, "rendered", "title", "raw")
        );
    }

    static String extractDescription(Map<?, ?> prData) {
        return firstNonBlank(
                stringValue(prData.get("description")),
                nestedString(prData, "rendered", "description", "raw"),
                nestedString(prData, "summary", "raw")
        );
    }

    private static String nestedString(Map<?, ?> root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(key);
        }
        return stringValue(current);
    }

    private static String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @Override
    public void postGlobalComment(String workspace, String repository, String prId, String content) {
        log.info("Posting global comment to PR {}", prId);
        try {
            restClient.post()
                    .uri("/repositories/{workspace}/{repo}/pullrequests/{id}/comments", workspace, repository, prId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", Map.of("raw", content)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to post global comment: {}", e.getMessage());
        }
    }

    @Override
    public void postLineComment(String workspace, String repository, String prId, String filePath, int lineNumber, String content) {
        log.info("Posting inline comment to PR {} at {}:{}", prId, filePath, lineNumber);
        try {
            restClient.post()
                    .uri("/repositories/{workspace}/{repo}/pullrequests/{id}/comments", workspace, repository, prId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "content", Map.of("raw", content),
                            "inline", Map.of(
                                    "to", lineNumber,
                                    "path", filePath
                            )
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to post line comment: {}", e.getMessage());
        }
    }
}
