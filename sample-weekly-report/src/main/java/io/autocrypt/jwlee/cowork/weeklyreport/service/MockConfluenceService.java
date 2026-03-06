package io.autocrypt.jwlee.cowork.weeklyreport.service;

import io.autocrypt.jwlee.cowork.weeklyreport.dto.MeetingInfo;
import io.autocrypt.jwlee.cowork.weeklyreport.dto.OkrInfo;
import io.autocrypt.jwlee.cowork.weeklyreport.dto.TeamReportInfo;

import java.util.List;

public class MockConfluenceService implements ConfluenceService {

    @Override
    public OkrInfo getOkr() {
        return new OkrInfo("2025 Q1", List.of(
            "V2X-EE 유럽 사업 성공적 진입",
            "PKI 제품 성능 고도화"
        ), "2025년 1분기 OKR");
    }

    @Override
    public List<TeamReportInfo> getTeamReports(String meetingPageId) {
        return List.of(
            new TeamReportInfo("개발그룹", "- [하만/볼보 트럭] LCM 마이그레이션 진행 중")
        );
    }

    @Override
    public List<MeetingInfo> getRecentMeetingUrls() {
        return List.of(
            new MeetingInfo("m1", "2025-02-28 주간 팀장 회의 (Mock)"),
            new MeetingInfo("m2", "2025-02-21 주간 팀장 회의 (Mock)")
        );
    }

    @Override
    public String getPageStorage(String pageId) {
        return "<html><body>Mock Page Content</body></html>";
    }
}
