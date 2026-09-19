package com.nextgentechforge.springai.tools;

import com.nextgentechforge.springai.dto.WebSearchResponse;
import com.nextgentechforge.springai.service.WebSearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WebSearchTools {
    private final WebSearchService search;
    public WebSearchTools(WebSearchService search) { this.search = search; }

    @Tool(description = "Search the public internet for current facts, releases, news or an explicit web lookup. Returns real titles, source URLs and snippets, not simulated DevOps data. Cite the returned URLs. Never include private information, credentials or internal documents in a query.")
    public WebSearchResponse searchWeb(@ToolParam(description = "A concise public search query, at most 300 characters") String query) {
        return search.search(query);
    }
}
