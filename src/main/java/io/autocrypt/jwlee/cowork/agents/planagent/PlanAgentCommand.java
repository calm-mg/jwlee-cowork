package io.autocrypt.jwlee.cowork.agents.planagent;

import io.autocrypt.jwlee.cowork.agents.planagent.AgentGenerationPlanAgent.AgentRequirement;
import io.autocrypt.jwlee.cowork.agents.planagent.AgentGenerationPlanAgent.DslResult;
import io.autocrypt.jwlee.cowork.core.commands.BaseAgentCommand;
import com.embabel.agent.core.AgentPlatform;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.Arrays;
import java.util.List;

@ShellComponent
public class PlanAgentCommand extends BaseAgentCommand {

    public PlanAgentCommand(AgentPlatform agentPlatform) {
        super(agentPlatform);
    }

    @ShellMethod(value = "에이전트 구현을 위한 DSL 설계도를 생성합니다.", key = "plan-agent")
    public String planAgent(
            @ShellOption(help = "에이전트의 목표 (예: '아침 업무 요약 생성')") String goal,
            @ShellOption(help = "주요 기능 (쉼표로 구분)", defaultValue = "") String features,
            @ShellOption(help = "제약 사항 (예: 'JiraExcelService 사용')", defaultValue = "") String constraints,
            @ShellOption(value = {"-p", "--show-prompts"}, defaultValue = "false", help = "Log prompts sent to the LLM") boolean p,
            @ShellOption(value = {"-r", "--show-responses"}, defaultValue = "false", help = "Log LLM responses") boolean r) {

        List<String> featureList = Arrays.asList(features.split(","));
        AgentRequirement req = new AgentRequirement(goal, featureList, constraints);

        System.out.println("\n[System] AgentGenerationPlanAgent를 구동하여 설계를 시작합니다...");

        try {
            // DslResult 타입으로 에이전트 호출
            DslResult result = invokeAgent(
                    DslResult.class,
                    getOptions(p, r),
                    req
            );

            if (result != null) {
                return "\n[Success] 에이전트 설계가 완료되었습니다! (guides/DSLs/ 폴더 확인)\n\n" + result.dslContent();
            } else {
                return "[System] 작업이 중단되었거나 최종 결과가 없습니다.";
            }

        } catch (Exception e) {
            return "[Error] 설계 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
