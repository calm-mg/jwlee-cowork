package io.autocrypt.jwlee.cowork.docdiffagent;

import com.embabel.agent.core.AgentPlatform;
import io.autocrypt.jwlee.cowork.core.commands.BaseAgentCommand;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.concurrent.ExecutionException;

/**
 * Spring Shell command for DocDiffAgent.
 * Performs deep-dive structural analysis and technical audit.
 */
@ShellComponent
public class DocDiffCommand extends BaseAgentCommand {

    public DocDiffCommand(AgentPlatform agentPlatform) {
        super(agentPlatform);
    }

    @ShellMethod(key = "doc-diff", value = "두 기술 문서의 버전 간 차이점 전수 분석 및 상세 보고서 생성")
    public String docDiff(
            @ShellOption(help = "소스 버전 이름 (예: 0.3.4)") String sourceVer,
            @ShellOption(help = "소스 파일 경로") String sourcePath,
            @ShellOption(help = "타겟 버전 이름 (예: 4.0.0)") String targetVer,
            @ShellOption(help = "타겟 파일 경로") String targetPath,
            @ShellOption(defaultValue = "false", help = "프롬프트 출력 여부") boolean p,
            @ShellOption(defaultValue = "false", help = "응답 출력 여부") boolean r
    ) throws ExecutionException, InterruptedException {

        logger.info("DocDiffCommand", String.format("🚀 Full-Scan Analysis started: %s -> %s", sourceVer, targetVer));

        var sourceDoc = new DocVersion(sourceVer, sourcePath);
        var targetDoc = new DocVersion(targetVer, targetPath);
        
        // Wrap into a single DiffRequest to avoid planner ambiguity
        var diffRequest = new DiffRequest(sourceDoc, targetDoc);

        // Invoke Agent with the consolidated request
        var result = invokeAgent(
                DocDiffReport.class,
                getOptions(p, r),
                diffRequest
        );

        if (result == null) {
            return "❌ 분석 실패: 상세 보고서를 생성하지 못했습니다.";
        }

        return "\n" + result.content();
    }
}
