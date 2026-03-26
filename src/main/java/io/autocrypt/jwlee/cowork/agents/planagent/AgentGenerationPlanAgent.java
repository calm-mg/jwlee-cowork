package io.autocrypt.jwlee.cowork.agents.planagent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoreFileTools;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.LocalRagTools;
import io.autocrypt.jwlee.cowork.core.workaround.JsonSafeToolishRag;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Agent(description = "사용자의 요구사항을 분석하여 Embabel Agent DSL을 생성하는 설계 에이전트")
@Component
public class AgentGenerationPlanAgent {

    public record AgentRequirement(String goal, List<String> features, String constraints) {}
    
    // 플래닝을 위한 유니크한 결과 타입
    public record DslResult(String dslContent) {}

    private final CoreFileTools fileTools;
    private final PromptProvider promptProvider;
    private final CoworkLogger logger;
    private final LocalRagTools localRagTools;

    public AgentGenerationPlanAgent(CoreFileTools fileTools, PromptProvider promptProvider, 
                                   CoworkLogger logger, LocalRagTools localRagTools) {
        this.fileTools = fileTools;
        this.promptProvider = promptProvider;
        this.logger = logger;
        this.localRagTools = localRagTools;
    }

    @AchievesGoal(description = "에이전트 구현을 위한 DSL 설계 완료")
    @Action
    public DslResult generateDsl(AgentRequirement req, Ai ai) throws IOException {
        logger.info("PlanAgent", "에이전트 설계 시작: " + req.goal());

        String ragName = "agent-design-guides";
        List<String> fewShots = List.of("guides/embabel-few-shot.md", "guides/spring-shell-few-shot.md");
        List<String> verifiedAgents;
        try (Stream<Path> paths = Files.walk(Paths.get("src/main/java/io/autocrypt/jwlee/cowork/agents"))) {
            verifiedAgents = paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("Agent.java"))
                .filter(p -> !p.toString().contains("/scaffold/"))
                .map(Path::toString).collect(Collectors.toList());
        }

        for (String path : fewShots) localRagTools.ingestUrlToMemory(path, ragName);
        for (String path : verifiedAgents) localRagTools.ingestUrlToMemory(path, ragName);
        localRagTools.ingestUrlToMemory("guides/embabel-0.3.4-full-guide.md", ragName);

        var searchOps = localRagTools.getOrOpenMemoryInstance(ragName);
        var rag = new JsonSafeToolishRag("embabel_knowledge", "Project patterns and framework guides", searchOps);

        String researchFindings = ai.withLlm(LlmOptions.fromModel("gemini-2.5-flash").withoutThinking())
                .withReference(rag)
                .generateText(promptProvider.getPrompt("agents/planagent/research-patterns.jinja", Map.of(
                    "goal", req.goal(), "features", req.features()
                )));

        // 2단계: Pro 모델로 DSL 텍스트 생성
        String dslContent = ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking())
                .generateText(promptProvider.getPrompt("agents/planagent/generate-dsl.jinja", Map.of(
                    "dsl_guide", fileTools.readFile("guides/DSL_GUIDE.md").content(),
                    "goal", req.goal(), "features", req.features(),
                    "constraints", req.constraints(), "research_findings", researchFindings
                )));

        // 파일 저장 로직
        String agentName = extractAgentName(dslContent);
        Path dslDir = Paths.get("guides/DSLs");
        if (!Files.exists(dslDir)) Files.createDirectories(dslDir);
        fileTools.writeFile(dslDir.resolve("DSL-" + agentName + ".md").toString(), dslContent);
        
        return new DslResult(dslContent);
    }

    private String extractAgentName(String content) {
        Pattern pattern = Pattern.compile("(?i)name:\\s*\"?(\\w+)\"?|# DSL-(\\w+)");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }
        return "GeneratedAgent-" + (System.currentTimeMillis() % 1000);
    }
}
