package io.autocrypt.jwlee.cowork.weeklyreport.service;

import io.autocrypt.jwlee.cowork.weeklyreport.dto.MeetingInfo;
import io.autocrypt.jwlee.cowork.weeklyreport.dto.OkrInfo;
import io.autocrypt.jwlee.cowork.weeklyreport.dto.TeamReportInfo;
import java.util.List;

public interface ConfluenceService {
    OkrInfo getOkr();
    List<TeamReportInfo> getTeamReports(String meetingUrl);
    List<MeetingInfo> getRecentMeetingUrls();
    String getPageStorage(String pageId);
}
