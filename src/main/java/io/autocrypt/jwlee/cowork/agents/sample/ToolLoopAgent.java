package io.autocrypt.jwlee.cowork.agents.sample;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.core.hitl.WaitFor;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.HasContent;

/**
 * [ToolLoopAgent: String-Controlled HITL Loop]
 * 1. UserDecision은 단순 String 입력만 받습니다.
 * 2. 입력에 "wantMore=false"가 포함되면 종료(SuccessReport), 아니면 루프(LoopbackReport).
 */
@Agent(description = "위키피디아 검색을 반복하며 수행하는 무한 루프 에이전트")
public class ToolLoopAgent {

    // --- Domain Models ---

    public record UserQuery(String query) {}
    public record SearchResult(String content) {}
    public record UserDecision(String input) {}

    /** 최종 보고서 인터페이스 */
    public sealed interface FinalReport permits SuccessReport, LoopbackReport {}

    public record SuccessReport(String summary) implements FinalReport, HasContent {
        @Override public String getContent() { return "# ✅ 최종 종료 보고\n" + summary; }
    }

    public record LoopbackReport(String nextQuery) implements FinalReport {}

    // --- Actions ---

    /** Step 1: 최초 검색 */
    @Action
    public SearchResult initialSearch(UserInput input, Ai ai) {
        return executeSearch(input.getContent(), ai);
    }

    /** Step 1-alt: 루프 검색 */
    @Action(canRerun = true)
    public SearchResult loopSearch(UserQuery query, Ai ai) {
        return executeSearch(query.query(), ai);
    }

    private SearchResult executeSearch(String q, Ai ai) {
        String res = ai.withDefaultLlm()
                .withToolGroup(CoreToolGroups.WEB)
                .generateText("Wikipedia search about: " + q);
        return new SearchResult(res);
    }

    /** Step 2: 결과 출력 및 다음 입력 대기 (HITL) */
    @Action(canRerun = true)
    public UserDecision askWhatNext(SearchResult result) {
        System.out.println("\n# 🔍 검색 결과:\n" + result.content() + "\n");
        return WaitFor.formSubmission("다음 검색어를 입력하세요. (종료하려면 'wantMore=false' 입력)", UserDecision.class);
    }

    /** Step 3: 문자열 분석을 통한 분기 처리 (사용자 제안 로직) */
    @Action(canRerun = true)
    public FinalReport processDecision(UserDecision decision) {
        if (decision.input() != null && decision.input().contains("wantMore=false")) {
            return new SuccessReport("사용자가 종료를 요청했습니다.");
        }
        // 그 외의 모든 입력은 다음 검색어로 간주
        return new LoopbackReport(decision.input());
    }

    /** Step 4-A: 루프백 (기록 삭제 후 재시작) */
    @Action(canRerun = true, clearBlackboard = true)
    public UserQuery restart(LoopbackReport report) {
        System.out.println("[System] 루프백 가동. 새로운 검색어: " + report.nextQuery());
        return new UserQuery(report.nextQuery());
    }

    /** Step 4-B: 종료 (목표 달성) */
    @AchievesGoal(description = "에이전트 종료")
    @Action
    public SuccessReport terminate(SuccessReport report) {
        return report;
    }
}
