package io.autocrypt.jwlee.cowork.agents.sample.hitl;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.domain.io.UserInput;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.concurrent.ExecutionException;

@ShellComponent
public class DemoHitlCommand {

    private final AgentPlatform agentPlatform;

    public DemoHitlCommand(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @ShellMethod(value = "Run the HITL workflow demo", key = "demo-hitl")
    public String demoHitl(@ShellOption(defaultValue = "Do something dangerous") String request) throws ExecutionException, InterruptedException {
        System.out.println("\n[System] Starting HITL demo. Request: " + request);

        // We use runAsync instead of invoke to avoid NPE when the agent enters WAITING state
        // runAsync returns a CompletableFuture holding the AgentProcess
        AgentProcess process = AgentInvocation
                .create(agentPlatform, DemoHitlAgent.DemoResult.class)
                .runAsync(new UserInput(request))
                .get();

        // The process runs asynchronously. Our @Async EventListener will catch the ApprovalRequestedEvent.
        // We just need to wait here until the process is fully finished.
        while (!process.getFinished()) {
            Thread.sleep(500);
        }

        // Extract the final result from the blackboard
        DemoHitlAgent.DemoResult result = process.resultOfType(DemoHitlAgent.DemoResult.class);

        if (result != null) {
            return "[System] Demo finished. Final Result: " + result.message();
        } else {
            return "[System] Demo interrupted or returned null.";
        }
    }
}