package io.autocrypt.jwlee.cowork.agents.translate;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.EarlyTerminationPolicy;
import com.embabel.agent.core.ProcessOptions;
import io.autocrypt.jwlee.cowork.core.hitl.ApplicationContextHolder;
import io.autocrypt.jwlee.cowork.core.hitl.NotificationEvent;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.File;
import java.util.concurrent.ExecutionException;

@ShellComponent
public class TranslateCommand {

    private final AgentPlatform agentPlatform;

    public TranslateCommand(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    private ProcessOptions getProcessOptions() {
        return ProcessOptions.DEFAULT.withAdditionalEarlyTerminationPolicy(EarlyTerminationPolicy.maxActions(500));
    }

    private String handleProcessCompletion(AgentProcess process) {
        if (process.getStatus() != AgentProcessStatusCode.COMPLETED) {
            String errorMsg = "번역 프로세스가 비정상 종료되었습니다: " + process.getStatus();
            ApplicationContextHolder.getPublisher().publishEvent(new NotificationEvent("번역 실패", errorMsg));
            return "Error: " + errorMsg;
        }

        TranslateAgent.TranslationResult result = process.resultOfType(TranslateAgent.TranslationResult.class);
        return result != null ? result.message() : "Translation Process completed without a result.";
    }

    @ShellMethod(value = "Start a new translation task.", key = "translate start")
    public String start(
            @ShellOption(help = "Path to the PDF file") String pdf,
            @ShellOption(defaultValue = ".translate_workspace", help = "Workspace directory name") String workspace) throws ExecutionException, InterruptedException {

        if (!new File(pdf).exists()) {
            return "Error: PDF file not found at " + pdf;
        }

        System.out.println("Starting translation process for " + pdf + " in workspace " + workspace);

        AgentProcess process = AgentInvocation
                .create(agentPlatform, TranslateAgent.TranslationResult.class)
                .withProcessOptions(getProcessOptions())
                .runAsync(new TranslateAgent.TranslateStartRequest(pdf, workspace))
                .get();

        while (!process.getFinished()) {
            Thread.sleep(500);
        }

        return handleProcessCompletion(process);
    }

    @ShellMethod(value = "Resume an existing translation task.", key = "translate resume")
    public String resume(
            @ShellOption(defaultValue = ".translate_workspace", help = "Workspace directory name") String workspace) throws ExecutionException, InterruptedException {

        File wsDir = new File(workspace);
        if (!wsDir.exists()) {
            return "Error: Workspace not found at " + workspace;
        }

        System.out.println("Resuming translation process from workspace " + workspace);

        AgentProcess process = AgentInvocation
                .create(agentPlatform, TranslateAgent.TranslationResult.class)
                .withProcessOptions(getProcessOptions())
                .runAsync(new TranslateAgent.TranslateResumeRequest(workspace))
                .get();

        while (!process.getFinished()) {
            Thread.sleep(500);
        }

        return handleProcessCompletion(process);
    }
}
