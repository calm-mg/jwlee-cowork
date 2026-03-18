package io.autocrypt.jwlee.cowork.agents.sample;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import io.autocrypt.jwlee.cowork.agents.sample.PoliteEmailAgent;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

/**
 * PoliteEmailAgent를 Spring Shell 명령어로 직접 노출합니다.
 * 사용법: polite-email "욕설 섞인 한국어 내용"
 */
@ShellComponent
public record EmailShellCommands(AgentPlatform agentPlatform) {

    @ShellMethod(value = "Directly transform angry Korean text into professional English email", key = "polite-email")
    public String politeEmail(
            @ShellOption(help = "The angry Korean text to sanitize") String text
    ) {
        System.out.println("\n[Embabel] Sanitizing and translating your message...\n");

        // AgentInvocation을 사용하여 특정 결과 타입(FinalEmail)을 목표로 에이전트 실행
        var invocation = AgentInvocation
                .create(agentPlatform, PoliteEmailAgent.FinalEmail.class);

        // 에이전트 호출 (내부적으로 GOAP 플랜 및 루프 수행)
        var result = invocation.invoke(new UserInput(text));

        // 최종 결과물 반환
        return result.getContent();
    }
}
