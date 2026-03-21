package io.autocrypt.jwlee.cowork.agents.sample.hitl;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.chat.AssistantMessage;
import io.autocrypt.jwlee.cowork.core.hitl.ApprovalDecision;
import io.autocrypt.jwlee.cowork.core.hitl.CoreApprovalState;
import org.springframework.stereotype.Component;

/**
 * A simple dummy agent to test the HITL workflow scaffolding.
 */
@Agent(description = "A demo agent showcasing Human-in-the-Loop workflow")
@Component
public class DemoHitlAgent {

    public record DemoResult(String message) {}

    @Action
    public CoreApprovalState proposePlan(UserInput input) {
        return new CoreApprovalState(
            "User requested: " + input.getContent(),
            "I plan to execute a dummy operation for demonstration."
        );
    }

    @Action
    @AchievesGoal(description = "The plan has been approved and executed.")
    public DemoResult executePlan(CoreApprovalState state, ApprovalDecision decision, ActionContext ctx) {
        if (decision.approved()) {
            ctx.sendMessage(new AssistantMessage("Plan executed successfully! Comment: " + decision.comment()));
            return new DemoResult("Success");
        } else {
            ctx.sendMessage(new AssistantMessage("Plan rejected. Reason: " + decision.comment()));
            return new DemoResult("Rejected");
        }
    }
}