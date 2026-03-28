package io.autocrypt.jwlee.cowork.core.commands;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.core.Verbosity;
import io.autocrypt.jwlee.cowork.core.tools.CoworkLogger;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Base class for Spring Shell commands that invoke Embabel agents.
 */
public abstract class BaseAgentCommand {

    protected final AgentPlatform agentPlatform;
    private AgentProcess lastProcess; // 마지막 실행 프로세스 보관

    protected BaseAgentCommand(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    protected ProcessOptions getOptions(boolean showPrompts, boolean showResponses) {
        return ProcessOptions.DEFAULT.withVerbosity(new Verbosity()
                .withShowPrompts(showPrompts)
                .withShowLlmResponses(showResponses));
    }

    /**
     * Helper to invoke an agent with consistent process handling and options.
     * Replaces invokeAgentProcess to maintain backward compatibility.
     */
    protected <T> T invokeAgent(Class<T> resultType, ProcessOptions options, Object... requests) 
            throws ExecutionException, InterruptedException {
        
        var invocation = AgentInvocation.create(agentPlatform, resultType)
                .withProcessOptions(options);

        int len = (requests == null) ? 0 : requests.length;

        lastProcess = switch (len) {
            case 0 -> invocation.runAsync(Collections.emptyMap()).get();
            case 1 -> invocation.runAsync(requests[0]).get();
            case 2 -> invocation.runAsync(requests[0], requests[1]).get();
            case 3 -> invocation.runAsync(requests[0], requests[1], requests[2]).get();
            case 4 -> invocation.runAsync(requests[0], requests[1], requests[2], requests[3]).get();
            case 5 -> invocation.runAsync(requests[0], requests[1], requests[2], requests[3], requests[4]).get();
            case 6 -> invocation.runAsync(requests[0], requests[1], requests[2], requests[3], requests[4], requests[5]).get();
            case 7 -> invocation.runAsync(requests[0], requests[1], requests[2], requests[3], requests[4], requests[5], requests[6]).get();
            case 8 -> invocation.runAsync(requests[0], requests[1], requests[2], requests[3], requests[4], requests[5], requests[6], requests[7]).get();
            case 9 -> invocation.runAsync(requests[0], requests[1], requests[2], requests[3], requests[4], requests[5], requests[6], requests[7], requests[8]).get();
            case 10 -> invocation.runAsync(requests[0], requests[1], requests[2], requests[3], requests[4], requests[5], requests[6], requests[7], requests[8], requests[9]).get();
            default -> throw new IllegalArgumentException("Too many arguments.");
        };

        while (!lastProcess.getFinished()) {
            Thread.sleep(500);
        }
        return lastProcess.resultOfType(resultType);
    }

    /**
     * Report metrics of the last executed process.
     */
    protected void reportOverallMetrics(CoworkLogger logger, String prefix) {
        if (lastProcess == null) return;

        // 총 비용 및 사용량
        logger.info(prefix, "[Total Process Metrics]\n" + lastProcess.costInfoString(true));

        // 완료된 액션 히스토리
        var history = lastProcess.getHistory();
        String historyLog = IntStream.range(0, history.size())
                .mapToObj(i -> String.format("%d. %s (%.1fs)", 
                        i + 1, 
                        history.get(i).getActionName(), 
                        history.get(i).getRunningTime().toMillis() / 1000.0))
                .collect(Collectors.joining("\n"));
        logger.info(prefix, "[Action Sequence]\n" + historyLog);
    }
}
