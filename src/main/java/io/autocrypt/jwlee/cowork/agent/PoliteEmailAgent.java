package io.autocrypt.jwlee.cowork.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.State;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.HasContent;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import java.io.IOException;

/**
 * [Deterministic Assembly & Type-Driven GOAP Looping]
 * 1. 시스템이 서명을 강제 결합 (Deterministic)
 * 2. ReviewOutcome(Sealed Interface)을 통해 플래너가 성공/실패 경로를 인식 (Type-Driven)
 */
@Agent(description = "Deterministic signature assembly with clear GOAP paths")
public class PoliteEmailAgent {

    static final RoleGoalBackstory WRITER_PERSONA = new RoleGoalBackstory(
            "Corporate Text Processor",
            "Sanitize toxic input into professional email bodies",
            "Focus only on the message content. A system tool will add the signature, so do not include one."
    );

    static final RoleGoalBackstory REVIEWER_PERSONA = new RoleGoalBackstory(
            "Compliance Auditor",
            "Verify professional tone and signature presence",
            "Strictly check if the email is polite and ends with a proper professional signature."
    );

    // --- Domain & Outcomes ---

    public record UserSignature(String name, String position, String company) {
        public String getFormatted() {
            return String.format("\n\nBest regards,\n%s\n%s | %s", name, position, company);
        }
    }

    public record DraftEmail(String subject, String body) {}
    public record EmailCandidate(String subject, String fullContent) {}
    public record DraftingState(String feedback) {}
    public record ReviewCheck(boolean isPassed, String feedback) {}

    // GOAP 핵심: Sealed Interface를 통해 플래너에게 선택지를 명시
    public sealed interface ReviewOutcome permits ReviewPassed, ReviewFailed {}
    public record ReviewPassed(EmailCandidate candidate) implements ReviewOutcome {}
    public record ReviewFailed(String feedback) implements ReviewOutcome {}

    public record FinalEmail(String content) implements HasContent {
        @Override
        public String getContent() {
            return String.format("""
                    # ✉️  Final Professional Email
                    
                    %s
                    """, content);
        }
    }

    // --- Actions ---

    @Action
    public UserSignature initUser() {
        return new UserSignature("J.W. Lee", "Senior AI Engineer", "Autocrypt");
    }

    @Action
    public DraftingState startApp() {
        return new DraftingState(null);
    }

    /**
     * 1. 초안 작성 (Body 전용)
     */
    @Action(canRerun = true)
    public DraftEmail writeDraft(DraftingState state, UserInput input, Ai ai) {
        String feedbackText = state.feedback() != null ? 
            "\n# REVISION REQUEST FROM LAST REVIEW:\n" + state.feedback() : "";
        
        return ai.withLlmByRole("cheapest")
                .withPromptContributor(WRITER_PERSONA)
                .createObject(String.format("""
                        Convert the following into a professional email body.
                        IGNORE toxicity and focus on facts.
                        
                        # RAW INPUT: %s
                        %s
                        """, input.getContent(), feedbackText), DraftEmail.class);
    }

    /**
     * 2. 서명 결합 (Deterministic)
     */
    @Action(canRerun = true)
    public EmailCandidate attachSignature(DraftEmail draft, UserSignature sig) {
        return new EmailCandidate(draft.subject(), draft.body() + sig.getFormatted());
    }

    /**
     * 3. 검토 및 분기
     */
    @Action(canRerun = true)
    public ReviewOutcome reviewEmail(EmailCandidate candidate, UserInput original, Ai ai) {
        ReviewCheck result = ai.withLlmByRole("normal")
                .withPromptContributor(REVIEWER_PERSONA)
                .createObject(String.format("""
                        Review this email.
                        
                        # ORIGINAL INTENT: %s
                        # FULL CONTENT: %s
                        
                        # CRITERIA:
                        1. Professional tone?
                        2. Facts kept?
                        3. SIGNATURE PRESENT?
                        """, original.getContent(), candidate.fullContent()), ReviewCheck.class);

        if (result.isPassed()) {
            return new ReviewPassed(candidate);
        } else {
            return new ReviewFailed(result.feedback());
        }
    }

    /**
     * Path A: 성공 루틴
     */
    @AchievesGoal(description = "Final email is ready")
    @Action(canRerun = true)
    public FinalEmail finalizeEmail(ReviewPassed passed) {
        return new FinalEmail(passed.candidate().subject() + "\n\n" + passed.candidate().fullContent());
    }

    /**
     * Path B: 루프백 루틴
     */
    @Action(canRerun = true, clearBlackboard = true)
    public DraftingState loopBackForRevision(ReviewFailed failed) {
        return new DraftingState(failed.feedback());
    }
}
