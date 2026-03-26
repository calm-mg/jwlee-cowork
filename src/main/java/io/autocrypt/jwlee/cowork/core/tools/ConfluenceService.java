package io.autocrypt.jwlee.cowork.core.tools;

public interface ConfluenceService {
    
    record ConfluencePageInfo(String id, String title, String content) {
        public boolean isEmpty() {
            return content == null || content.isEmpty();
        }
    }

    ConfluencePageInfo getCurrentOkr();
    ConfluencePageInfo getCurrentWeeklyReport();
    String getPageStorage(String pageId);
}
