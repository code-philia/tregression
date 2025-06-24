package com.github.myxxxsquared.gpt_invoker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class GPTUtils {
    private static final Pattern CODE_PATTERN = Pattern.compile("```(?:\\w+\\s+)?([\\s\\S]*?)```");
    private static final Pattern JSON_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)```");

    public static String extractCode(String message) throws Exception {
        Matcher matcher = CODE_PATTERN.matcher(message);
        if (!matcher.find()) {
            throw new Exception("No code block found in the response.");
        }
        String code = matcher.group(1).trim();
        if (matcher.find()) {
            throw new Exception("Multiple code blocks found in the response.");
        }
        return code;
    }

    public static JsonElement extractJson(String message) throws Exception {
        Matcher matcher = JSON_PATTERN.matcher(message);
        if (!matcher.find()) {
            throw new Exception("No JSON block found in the response.");
        }
        String jsonStr = matcher.group(1).trim();
        if (matcher.find()) {
            throw new Exception("Multiple JSON blocks found in the response.");
        }
        try {
            return JsonParser.parseString(jsonStr);
        } catch (JsonSyntaxException e) {
            throw new Exception("Failed to parse JSON block.", e);
        }
    }
}
