package io.autocrypt.jwlee.cowork.bitbucketprapp;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ProcessOptions;
import io.autocrypt.jwlee.cowork.core.commands.BaseAgentCommand;
import io.autocrypt.jwlee.cowork.bitbucketprapp.BitbucketPrReviewAgent.PrReviewRequest;
import io.autocrypt.jwlee.cowork.bitbucketprapp.BitbucketPrReviewAgent.InitialState;
import io.autocrypt.jwlee.cowork.bitbucketprapp.BitbucketPrReviewAgent.FinalReviewReport;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class BitbucketPrReviewCommand extends BaseAgentCommand {

    public BitbucketPrReviewCommand(AgentPlatform agentPlatform) {
        super(agentPlatform);
    }

    @ShellMethod(value = "Bitbucket PR 리뷰를 수행합니다.", key = "pr-review")
    public void prReview(
            @ShellOption(help = "저장소 슬러그 (예: autocrypt/securityplatform)", defaultValue = "autocrypt/securityplatform") String repo,
            @ShellOption(help = "Pull Request ID") Long prId,
            @ShellOption(help = "제품 매뉴얼 폴더 경로", defaultValue = ShellOption.NULL) String manuals,
            @ShellOption(help = "표준 문서 폴더 경로", defaultValue = ShellOption.NULL) String standards,
            @ShellOption(help = "스타일 가이드 Confluence URL (/wiki/pages/<id>, viewpage.action?pageId=<id>, 또는 기존 short URL)") String styleGuide,
            @ShellOption(help = "아키텍처 가이드 Confluence URL (/wiki/pages/<id>, viewpage.action?pageId=<id>, 또는 기존 short URL)") String archGuide,
            @ShellOption(help = "프롬프트 표시 여부", defaultValue = "false") boolean showPrompts,
            @ShellOption(help = "응답 표시 여부", defaultValue = "false") boolean showResponses
    ) throws Exception {
        PrReviewRequest request = new PrReviewRequest(repo, prId, manuals, standards, styleGuide, archGuide);
        InitialState initialState = new InitialState(request);

        ProcessOptions options = getOptions(showPrompts, showResponses);
        FinalReviewReport report = invokeAgent(FinalReviewReport.class, options, initialState);
        
        System.out.println("Review completed successfully.");
        System.out.println(report.summary());
    }
}
