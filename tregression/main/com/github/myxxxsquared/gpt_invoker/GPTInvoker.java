package com.github.myxxxsquared.gpt_invoker;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public class GPTInvoker {
    private final String apiKey, model, apiHost;
    private final boolean readCache, writeCache;
    private final Connection sqlConn;
    private final Usage usage;
    private final Gson gson = new Gson();
    private final File dumpFolder;

    public GPTInvoker(String apiKey, String model, String apiHost,
            boolean readCache, boolean writeCache,
            String cachePath, String dumpFolder, boolean dumpLogEnabled) throws Exception {
        this.apiKey = apiKey;
        this.model = model;
        this.apiHost = apiHost;
        this.readCache = readCache;
        this.writeCache = writeCache;
        this.usage = new Usage(model);

        if (readCache || writeCache) {
            sqlConn = DriverManager.getConnection("jdbc:sqlite:" + cachePath);
            try (Statement st = sqlConn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS gpt_cache(" +
                        "cache_digest TEXT PRIMARY KEY, " +
                        "cache_prompt TEXT, cache_response TEXT)");
            }
        } else
            sqlConn = null;

        this.dumpFolder = dumpFolder != null ? new File(dumpFolder) : null;
        if (this.dumpFolder != null)
            this.dumpFolder.mkdirs();
    }

    private String sha1(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte x : b)
            sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private JsonObject queryCache(String digest, String concat) throws Exception {
        if (sqlConn == null || !readCache)
            return null;
        try (PreparedStatement pst = sqlConn.prepareStatement(
                "SELECT cache_prompt, cache_response FROM gpt_cache WHERE cache_digest=?")) {
            pst.setString(1, digest);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next() && concat.equals(rs.getString(1))) {
                    return gson.fromJson(rs.getString(2), JsonObject.class);
                }
            }
        }
        return null;
    }

    private void putCache(String digest, String concat, JsonObject resp) throws Exception {
        if (sqlConn == null || !writeCache)
            return;
        try (PreparedStatement check = sqlConn.prepareStatement(
                "SELECT cache_prompt FROM gpt_cache WHERE cache_digest=?")) {
            check.setString(1, digest);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next())
                    return;
            }
        }
        try (PreparedStatement ins = sqlConn.prepareStatement(
                "INSERT INTO gpt_cache(cache_digest,cache_prompt,cache_response) VALUES(?,?,?)")) {
            ins.setString(1, digest);
            ins.setString(2, concat);
            ins.setString(3, resp.toString());
            ins.executeUpdate();
        }
    }

    private JsonObject callApi(JsonArray messages) throws Exception {
        URL url = new URI(apiHost + "/chat/completions").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", 0.0);
        body.addProperty("top_p", 0.9);
        body.addProperty("max_tokens", 4096);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        InputStream is = conn.getInputStream();
        String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        return gson.fromJson(resp, JsonObject.class);
    }

    public String generate(List<Map<String, Object>> msgs) throws Exception {
        JsonArray ja = gson.toJsonTree(msgs, new TypeToken<JsonArray>() {
        }.getType()).getAsJsonArray();
        String concat = ja.toString();
        String digest = sha1(concat);

        JsonObject cacheObj = queryCache(digest, concat);
        if (cacheObj != null) {
            usage.update(
                    cacheObj.getAsJsonObject("usage").get("prompt_tokens").getAsLong(),
                    cacheObj.getAsJsonObject("usage").get("completion_tokens").getAsLong(),
                    true);
            dumpLog(msgs, cacheObj, true, false, null);
            return extractContent(cacheObj);
        }

        JsonObject resp = callApi(ja);
        usage.update(
                resp.getAsJsonObject("usage").get("prompt_tokens").getAsLong(),
                resp.getAsJsonObject("usage").get("completion_tokens").getAsLong(),
                false);
        putCache(digest, concat, resp);
        dumpLog(msgs, resp, false, false, null);

        return extractContent(resp);
    }

    private String extractContent(JsonObject resp) {
        return resp.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
    }

    private void dumpLog(List<Map<String, Object>> msgs, JsonObject resp,
            boolean fromCache, boolean isError, String errMsg) {
        if (dumpFolder == null)
            return;
        try {
            String time = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS").format(new Date());
            File f = new File(dumpFolder, time + (fromCache ? "-C" : "-N") + ".json");
            try (FileWriter w = new FileWriter(f)) {
                JsonObject o = new JsonObject();
                o.add("messages", gson.toJsonTree(msgs));
                if (resp != null)
                    o.add("response", resp);
                if (errMsg != null)
                    o.addProperty("error", errMsg);
                w.write(gson.toJson(o));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
