package io.autocrypt.jwlee.cowork.agents.morningbriefing;

import com.embabel.agent.core.AgentPlatform;
import io.autocrypt.jwlee.cowork.agents.morningbriefing.MorningBriefingAgent.BriefingRequest;
import io.autocrypt.jwlee.cowork.agents.morningbriefing.MorningBriefingAgent.MorningBriefingReport;
import io.autocrypt.jwlee.cowork.core.commands.BaseAgentCommand;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class MorningBriefingCommand extends BaseAgentCommand {

    public MorningBriefingCommand(AgentPlatform agentPlatform) {
        super(agentPlatform);
    }

    @ShellMethod(value = "어제자 활동을 분석하여 아침 업무 브리핑을 생성합니다.", key = "morning-briefing")
    public String morningBriefing(
            @ShellOption(help = "대상 날짜 (YYYY-MM-DD, 비어있으면 어제)", defaultValue = "") String targetDate,
            @ShellOption(value = {"-p", "--show-prompts"}, defaultValue = "false", help = "Log prompts sent to the LLM") boolean p,
            @ShellOption(value = {"-r", "--show-responses"}, defaultValue = "false", help = "Log LLM responses") boolean r) {

        System.out.println("\n[System] Morning Briefing 생성을 시작합니다...");

        try {
            // MorningBriefingReport 타입으로 에이전트 호출
            MorningBriefingReport result = invokeAgent(
                    MorningBriefingReport.class,
                    getOptions(p, r),
                    new BriefingRequest(targetDate)
            );

            if (result != null) {
                return "\n[Success] Morning Briefing 생성이 완료되었습니다.\n\n" + result.content();
            } else {
                return "[System] 작업이 중단되었거나 결과가 없습니다.";
            }

        } catch (Exception e) {
            return "[Error] 브리핑 생성 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}
