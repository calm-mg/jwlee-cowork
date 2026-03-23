package io.autocrypt.jwlee.cowork.core.workaround;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.rag.service.SearchOperations;
import com.embabel.agent.rag.tools.ToolishRag;
import com.embabel.common.ai.prompt.PromptContributor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JsonSafeToolishRag implements LlmReference {

    private final ToolishRag delegate;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public JsonSafeToolishRag(String name, String description, SearchOperations searchOperations) {
        this.delegate = new ToolishRag(name, description, searchOperations);
    }

    private JsonSafeToolishRag(ToolishRag delegate) {
        this.delegate = delegate;
    }

    public JsonSafeToolishRag withHint(PromptContributor hint) {
        return new JsonSafeToolishRag(this.delegate.withHint(hint));
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public String notes() {
        return delegate.notes();
    }

    @Override
    public String contribution() {
        return delegate.contribution();
    }

    @Override
    public List<Tool> tools() {
        return delegate.tools().stream().map(this::wrapTool).collect(Collectors.toList());
    }

    private Tool wrapTool(Tool originalTool) {
        return (Tool) Proxy.newProxyInstance(
                JsonSafeToolishRag.class.getClassLoader(),
                new Class<?>[]{Tool.class},
                (proxy, method, args) -> {
                    if ("call".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof String) {
                        Tool.Result originalResult = (Tool.Result) method.invoke(originalTool, args);
                        return wrapResult(originalResult);
                    }
                    return method.invoke(originalTool, args);
                }
        );
    }

    private Tool.Result wrapResult(Tool.Result originalResult) {
        try {
            Method getContent = originalResult.getClass().getMethod("getContent");
            String content = (String) getContent.invoke(originalResult);
            return Tool.Result.text(wrapInJson(content));
        } catch (Exception e) {
            String str = originalResult.toString();
            String content = str;
            if (str.startsWith("TextResult(content=")) {
                content = str.substring("TextResult(content=".length(), str.length() - 1);
            } else if (str.startsWith("Success(content=")) {
                content = str.substring("Success(content=".length(), str.length() - 1);
            }
            return Tool.Result.text(wrapInJson(content));
        }
    }

    private String wrapInJson(String content) {
        if (content == null) content = "";
        
        String trimmed = content.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // Already JSON, do not double-wrap
            return content;
        }
        
        try {
            Map<String, String> map = new HashMap<>();
            map.put("result", content);
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            // Fallback in the highly unlikely event that serialization fails
            return "{\"result\": \"Error serializing content: " + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
