package io.autocrypt.jwlee.cowork.opsagent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.common.ai.model.LlmOptions;
import io.autocrypt.jwlee.cowork.core.prompts.PromptProvider;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;
import io.autocrypt.jwlee.cowork.core.tools.FileReadTool;
import io.autocrypt.jwlee.cowork.core.tools.GlobTool;
import io.autocrypt.jwlee.cowork.core.tools.GrepTool;
import io.autocrypt.jwlee.cowork.opsagent.domain.OpsRequest;
import io.autocrypt.jwlee.cowork.opsagent.domain.OpsResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Agent(description = "Analyzes repository infrastructure, operational mode, HA, and IaC status.")
@Component
public class OpsAgent {

    private final GrepTool grepTool;
    private final FileReadTool readTool;
    private final GlobTool globTool;
    private final PromptProvider promptProvider;
    private final RoleGoalBackstory persona;
    private final CoworkLogger logger;
    private final com.embabel.agent.core.AgentPlatform agentPlatform;

    public OpsAgent(GrepTool grepTool, FileReadTool readTool, GlobTool globTool, PromptProvider promptProvider, CoworkLogger logger, com.embabel.agent.core.AgentPlatform agentPlatform) {
        this.grepTool = grepTool;
        this.readTool = readTool;
        this.globTool = globTool;
        this.promptProvider = promptProvider;
        this.logger = logger;
        this.agentPlatform = agentPlatform;
        this.persona = promptProvider.getPersona("agents/ops/persona.md");
    }

    // --- DTOs for LLM Extraction ---

    public record InfrastructureEvidence(String category, String fileName, String snippet, String reasoning) {}

    public record DeploymentAnalysis(String type, double confidence, String evidenceSummary, String platformDetails) {}

    public record ReliabilityAnalysis(boolean haEnabled, String sessionSharing, String databaseRedundancy, String scalabilityNotes) {}

    public record SecurityObservabilityAnalysis(String secretsManagement, boolean hsmIntegration, List<String> monitoringTools, List<String> securityGaps) {}

    // --- States ---

    @State
    public record OpsPrimingState(OpsRequest request, String archContext) {}

    @State
    public record OpsDiscoveryState(OpsRequest request, List<InfrastructureEvidence> evidenceList, String techStack) {}

    @State
    public record OpsCategorizationState(OpsRequest request, DeploymentAnalysis deployment, ReliabilityAnalysis reliability, SecurityObservabilityAnalysis securityObs) {}

    // --- Actions ---

    @Action(description = "Stage 0: Context Priming via ArchitectureAgent.")
    public OpsPrimingState prepareOpsContext(OpsRequest request) {
        logger.info("OpsAgent", "Stage 0: Priming context via ArchitectureAgent...");
        String archContext = "No prior architecture context available.";
        try {
            var archInvocation = com.embabel.agent.api.invocation.AgentInvocation.create(agentPlatform, io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureReport.class);
            var archReport = archInvocation.invoke(new io.autocrypt.jwlee.cowork.architectureagent.domain.ArchitectureRequest(request.path(), "Infrastructure & Operations Analysis Context"));
            archContext = String.format("Summary: %s\nTech Stack: %s\nModules: %d", archReport.summary(), archReport.technicalStack(), archReport.modules().size());
        } catch (Exception e) {
            logger.info("OpsAgent", "Architecture priming failed: " + e.getMessage());
        }
        return new OpsPrimingState(request, archContext);
    }

    @Action(description = "Stage 1: Scanning repository for infrastructure evidence.")
    public OpsDiscoveryState discoverInfrastructureFiles(OpsPrimingState state) {
        logger.info("OpsAgent", "Stage 1: Discovering infrastructure files and patterns...");
        List<InfrastructureEvidence> evidence = new ArrayList<>();
        String path = state.request().path();

        // 1. Glob for common infrastructure files
        List<String> infraFiles = new ArrayList<>();
        try {
            infraFiles.addAll(globTool.glob("**/{Dockerfile,docker-compose.yml,*.tf,Jenkinsfile,*.yaml,*.yml,*.sh,pom.xml,package.json}"));
        } catch (Exception e) {
            logger.info("OpsAgent", "Glob failed: " + e.getMessage());
        }
        for (String file : infraFiles) {
            if (isValidInfraFile(file)) {
                try {
                    String content = readTool.readFile(file).content();
                    String category = inferCategory(file);
                    evidence.add(new InfrastructureEvidence(category, file, truncate(content, 1000), "Found via file pattern."));
                } catch (Exception ignored) {}
            }
        }

        // 2. Grep for operational keywords
        List<String> keywords = Arrays.asList("pkcs11", "udev", "systemd", "sysctl", "HSM", "Redis", "DataSource", "health", "vault", "secret", "prometheus", "grafana", "logback");
        for (String kw : keywords) {
            List<String> matches = grepTool.grep(kw, path);
            for (int i = 0; i < Math.min(matches.size(), 3); i++) { // Sample first 3 matches
                String match = matches.get(i);
                if (match.contains(":")) {
                    String file = match.split(":", 2)[0];
                    if (isValidInfraFile(file)) {
                        evidence.add(new InfrastructureEvidence("PatternMatch", file, match, "Found keyword: " + kw));
                    }
                }
            }
        }

        return new OpsDiscoveryState(state.request(), evidence, state.archContext());
    }

    @Action(description = "Stage 2: Analyzing operational layers (Deployment, Reliability, Security).")
    public OpsCategorizationState analyzeOpsLayers(OpsDiscoveryState state, Ai ai) {
        logger.info("OpsAgent", "Stage 2: Deeply analyzing infrastructure evidence...");

        String evidenceDump = state.evidenceList().stream()
                .map(e -> String.format("[%s] %s: %s", e.category(), e.fileName(), e.snippet()))
                .collect(Collectors.joining("\n---\n"));

        String prompt = promptProvider.getPrompt("agents/ops/analyze-layers.jinja", Map.of(
            "techStack", state.techStack(),
            "evidence", truncate(evidenceDump, 60000)
        ));

        return ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking().withMaxTokens(65536))
                .creating(OpsCategorizationState.class)
                .fromPrompt(prompt);
    }

    @AchievesGoal(description = "Stage 3: Synthesizing the final Operations Report.")
    @Action(description = "Compiling the final Operations & Infrastructure report.")
    public OpsResult synthesizeOpsReport(OpsCategorizationState state, Ai ai) {
        logger.info("OpsAgent", "Stage 3: Synthesizing final report...");

        String prompt = promptProvider.getPrompt("agents/ops/synthesize-report.jinja", Map.of(
            "deployment", state.deployment(),
            "reliability", state.reliability(),
            "securityObs", state.securityObs()
        ));

        String report = ai.withLlm(LlmOptions.withLlmForRole("performant").withoutThinking().withMaxTokens(65536))
                .withPromptContributor(persona)
                .generateText(prompt);

        return new OpsResult(report, "Success");
    }

    // --- Helpers ---

    private boolean isValidInfraFile(String path) {
        String p = path.toLowerCase();
        return !p.contains("/target/") && !p.contains("/build/") && !p.contains("/node_modules/") && !p.contains("/.git/");
    }

    private String inferCategory(String file) {
        if (file.contains("Dockerfile") || file.contains("docker-compose") || file.endsWith(".yaml") || file.endsWith(".yml")) return "Deployment/Infrastucture";
        if (file.endsWith(".tf")) return "IaC";
        if (file.contains("Jenkins") || file.contains(".github")) return "CI/CD";
        if (file.contains("pom.xml") || file.contains("package.json")) return "Dependency";
        return "GeneralConfig";
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n... [TRUNCATED]";
    }
}
