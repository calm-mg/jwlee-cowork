package io.autocrypt.jwlee.cowork.docdiffagent;

import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import io.autocrypt.jwlee.cowork.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test for the refined DocDiffAgent.
 * Verifies the simplified two-stage GOAP workflow.
 */
class DocDiffIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DocDiffAgent docDiffAgent;

    @Autowired
    private Ai ai;

    @Test
    void testConsolidatedDocDiffFlow() throws IOException {
        String sourceVer = "0.3.4";
        String sourcePath = "0.3.4.md";
        String targetVer = "4.0.0";
        String targetPath = "4.0.0.md";

        System.out.println("🚀 Starting Consolidated DocDiff Flow...");

        // ActionContext Mocking
        ActionContext ctx = mock(ActionContext.class);
        when(ctx.ai()).thenReturn(ai);

        DiffRequest request = new DiffRequest(
                new DocVersion(sourceVer, sourcePath),
                new DocVersion(targetVer, targetPath)
        );

        // 1. Stage 1: Mapping
        System.out.println("--- Step 1: Mapping ---");
        TOCMapResult mapResult = docDiffAgent.prepareAnalysisMap(request, ctx);
        assertNotNull(mapResult);

        // 2. Stage 2: Full Analysis & Synthesis (Internal Loop)
        System.out.println("--- Step 2: Full-Scan Analysis & Synthesis ---");
        DocDiffReport report = docDiffAgent.generateDetailedReport(mapResult, request, ctx);

        assertNotNull(report);
        System.out.println("\n✅ Final report generated and saved by agent.");
        System.out.println("Report Preview:\n" + report.content().substring(0, Math.min(1000, report.content().length())) + "...");
    }
}
