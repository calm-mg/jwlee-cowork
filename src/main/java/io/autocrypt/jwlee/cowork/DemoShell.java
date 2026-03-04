package io.autocrypt.jwlee.cowork;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import io.autocrypt.jwlee.cowork.agent.RootCauseAnalysisAgent;
import io.autocrypt.jwlee.cowork.agent.WriteAndReviewAgent;
import io.autocrypt.jwlee.cowork.injected.InjectedDemo;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
record DemoShell(InjectedDemo injectedDemo, AgentPlatform agentPlatform) {

    @ShellMethod("Demo")
    String demo() {
        // Illustrate calling an agent programmatically,
        // as most often occurs in real applications.
        var reviewedStory = AgentInvocation
                .create(agentPlatform, WriteAndReviewAgent.ReviewedStory.class)
                .invoke(new UserInput("Tell me a story about caterpillars"));
        return reviewedStory.getContent();
    }

    @ShellMethod("Invent an animal")
    String animal() {
        return injectedDemo.inventAnimal().toString();
    }

    @ShellMethod("Perform Root Cause Analysis (Automatically syncs knowledge base before analysis)")
    String rcaAnalyze(@ShellOption(defaultValue = "최근 결제 API 장애 사례와 대응 방법을 요약해줘.") String query) {
        var report = AgentInvocation
                .create(agentPlatform, RootCauseAnalysisAgent.RcaReport.class)
                .invoke(new UserInput(query));

        return String.format("""
                [ROOT CAUSE ANALYSIS REPORT]
                - Summary: %s
                - Cause: %s
                - Evidence: %s
                - Mitigation: %s
                """, report.incidentSummary(), report.likelyRootCause(), report.supportingEvidence(), report.suggestedMitigation());
    }}
