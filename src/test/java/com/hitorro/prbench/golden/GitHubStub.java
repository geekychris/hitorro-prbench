package com.hitorro.prbench.golden;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * A fake GitHub REST API on localhost with fixed responses, so the golden test covers the endpoints that
 * call GitHub (and what the app sends there) without a network or a token. Responses are literal JSON
 * strings (no Jackson), so the stub is identical on the Jackson 2 and Jackson 3 builds.
 */
public final class GitHubStub implements AutoCloseable {

    private final HttpServer server;
    private final List<String> requests = new ArrayList<>();

    public GitHubStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** The requests received since the last call: method, path with query, and body. */
    public synchronized String drain() {
        String s = String.join("\n", requests);
        requests.clear();
        return s;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().toString();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        synchronized (this) {
            requests.add(method + " " + path + (body.isEmpty() ? "" : " " + body));
        }
        String[] r = respond(method, path.replaceFirst("[?].*$", ""), path, body);
        byte[] out = r[1].getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        int status = Integer.parseInt(r[0]);
        ex.sendResponseHeaders(status, status == 204 ? -1 : out.length);
        if (status != 204) ex.getResponseBody().write(out);
        ex.close();
    }

    private static String b64(String s) {
        return Base64.getMimeEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String repo(String owner, String name, boolean fork) {
        return "{\"id\":101,\"name\":\"" + name + "\",\"full_name\":\"" + owner + "/" + name + "\","
                + "\"owner\":{\"login\":\"" + owner + "\",\"id\":7},\"private\":false,\"fork\":" + fork + ","
                + "\"html_url\":\"https://github.com/" + owner + "/" + name + "\","
                + "\"description\":\"Golden repo \\\"" + name + "\\\" é\",\"language\":\"Java\","
                + "\"stargazers_count\":12,\"forks_count\":1,\"default_branch\":\"main\","
                + "\"topics\":[\"java\",\"golden\"],\"archived\":false,\"size\":1234,"
                + "\"created_at\":\"2020-01-02T03:04:05Z\",\"updated_at\":\"2024-05-06T07:08:09Z\","
                + "\"pushed_at\":\"2024-05-06T07:08:09Z\",\"license\":null,\"homepage\":null}";
    }

    private static String pr(int n) {
        return "{\"id\":5005,\"number\":" + n + ",\"state\":\"closed\",\"title\":\"Fix the thing #" + n + "\","
                + "\"body\":null,\"html_url\":\"https://github.com/golden/app/pull/" + n + "\","
                + "\"user\":{\"login\":\"alice\",\"id\":9},"
                + "\"base\":{\"ref\":\"main\",\"sha\":\"1111111111111111111111111111111111111111\"},"
                + "\"head\":{\"ref\":\"fix/thing\",\"sha\":\"2222222222222222222222222222222222222222\"},"
                + "\"merged_at\":\"2024-02-03T04:05:06Z\",\"created_at\":\"2024-02-01T00:00:00Z\","
                + "\"changed_files\":3,\"additions\":40,\"deletions\":2,\"draft\":false,"
                + "\"labels\":[{\"name\":\"bug\"},{\"name\":\"golden\"}]}";
    }

    private static String comment(long id, String user, String path, String line, String body) {
        return "{\"id\":" + id + ",\"user\":{\"login\":\"" + user + "\"},\"path\":" + path + ",\"line\":" + line
                + ",\"diff_hunk\":\"@@ -1,3 +1,4 @@\\n+int x = 1;\",\"body\":" + body
                + ",\"created_at\":\"2024-02-02T10:11:12Z\",\"html_url\":\"https://github.com/c/" + id + "\"}";
    }

    private String[] respond(String method, String p, String full, String body) {
        if (p.equals("/rate_limit")) {
            return ok("{\"resources\":{\"core\":{\"limit\":5000,\"used\":1,\"remaining\":4999,\"reset\":1700000000}},"
                    + "\"rate\":{\"limit\":5000,\"used\":1,\"remaining\":4999,\"reset\":1700000000}}");
        }
        if (p.equals("/user")) return ok("{\"login\":\"golden\",\"id\":7,\"name\":\"Golden Tester\"}");
        if (p.equals("/user/orgs")) {
            return ok(full.contains("page=1") ? "[{\"login\":\"golden-org\",\"id\":70,\"description\":null}]" : "[]");
        }
        if (p.equals("/user/repos") || p.matches("/orgs/[^/]+/repos")) {
            String owner = p.startsWith("/orgs/") ? p.split("/")[2] : "golden";
            return ok(full.contains("page=1") ? "[" + repo(owner, "app", false) + "," + repo(owner, "lib", true) + "]" : "[]");
        }
        if (p.matches("/repos/[^/]+/[^/]+")) {
            String[] s = p.split("/");
            return ok(repo(s[2], s[3], false));
        }
        if (p.matches("/repos/[^/]+/[^/]+/topics")) {
            if (method.equals("PUT")) return ok(body);
            return ok("{\"names\":[\"java\",\"golden\"]}");
        }
        if (p.endsWith("/readme")) {
            return ok("{\"name\":\"README.md\",\"path\":\"README.md\",\"encoding\":\"base64\",\"content\":\""
                    + b64("# Golden App\n\nA small app used by the golden test.\n").replace("\r\n", "\\n") + "\"}");
        }
        if (p.matches("/repos/[^/]+/[^/]+/contents")) {
            return ok("[{\"name\":\"README.md\",\"path\":\"README.md\",\"type\":\"file\",\"size\":40},"
                    + "{\"name\":\"docs\",\"path\":\"docs\",\"type\":\"dir\",\"size\":0},"
                    + "{\"name\":\"pom.xml\",\"path\":\"pom.xml\",\"type\":\"file\",\"size\":99}]");
        }
        if (p.matches("/repos/[^/]+/[^/]+/contents/.+")) {
            return ok("{\"name\":\"pom.xml\",\"encoding\":\"base64\",\"content\":\""
                    + b64("<project><artifactId>golden</artifactId></project>\n").replace("\r\n", "\\n") + "\"}");
        }
        if (p.matches("/repos/[^/]+/[^/]+/commits")) {
            return ok("[{\"sha\":\"3333333333333333333333333333333333333333\",\"commit\":{\"message\":\"Add feature\\n\\nDetails\","
                    + "\"author\":{\"name\":\"Alice\",\"date\":\"2024-03-01T00:00:00Z\"}}},"
                    + "{\"sha\":\"4444444444444444444444444444444444444444\",\"commit\":{\"message\":\"Initial\","
                    + "\"author\":{\"name\":\"Bob\",\"date\":\"2024-01-01T00:00:00Z\"}}}]");
        }
        if (p.matches("/repos/[^/]+/[^/]+/git/trees/.+")) {
            return ok("{\"sha\":\"5555\",\"truncated\":false,\"tree\":["
                    + "{\"path\":\"README.md\",\"type\":\"blob\",\"size\":40},"
                    + "{\"path\":\"docs\",\"type\":\"tree\"},"
                    + "{\"path\":\"docs/guide.md\",\"type\":\"blob\",\"size\":2048},"
                    + "{\"path\":\"CONTRIBUTING.md\",\"type\":\"blob\",\"size\":300},"
                    + "{\"path\":\"src/Main.java\",\"type\":\"blob\",\"size\":10}]}");
        }
        if (p.matches("/repos/[^/]+/[^/]+/pulls") && method.equals("GET")) {
            return ok(full.contains("page=2") ? "[]" : "[" + pr(5) + "," + pr(6) + "]");
        }
        if (p.matches("/repos/[^/]+/[^/]+/pulls/[0-9]+") && method.equals("GET")) {
            return ok(pr(Integer.parseInt(p.substring(p.lastIndexOf('/') + 1))));
        }
        if (p.matches("/repos/[^/]+/[^/]+/pulls/[0-9]+") && method.equals("PATCH")) {
            return ok(pr(Integer.parseInt(p.substring(p.lastIndexOf('/') + 1))));
        }
        if (p.endsWith("/comments") && p.contains("/pulls/")) {
            return ok(full.contains("page=1") ? "[" + comment(9001, "reviewer", "\"src/Main.java\"", "12",
                    "\"Possible null dereference of `x` here.\"") + "," + comment(9002, "reviewer", "\"src/Util.java\"", "null",
                    "\"Consider closing the stream \\u2014 resource leak.\"") + "]" : "[]");
        }
        if (p.endsWith("/reviews")) {
            return ok(full.contains("page=1") ? "[{\"id\":9100,\"user\":{\"login\":\"reviewer\"},\"body\":\"Looks good overall\","
                    + "\"state\":\"COMMENTED\",\"submitted_at\":\"2024-02-02T11:00:00Z\"},"
                    + "{\"id\":9101,\"user\":{\"login\":\"bot[bot]\"},\"body\":\"\",\"state\":\"APPROVED\"}]" : "[]");
        }
        if (p.endsWith("/comments") && p.contains("/issues/")) {
            return ok(full.contains("page=1") ? "[" + comment(9200, "carol", "null", "null", "\"Thanks!\"") + "]" : "[]");
        }
        if (p.endsWith("/check-runs")) return ok("{\"total_count\":0,\"check_runs\":[]}");
        if (p.contains("/git/refs") && method.equals("DELETE")) return new String[]{"204", ""};
        if (p.contains("/git/refs") && method.equals("POST")) return ok("{\"ref\":\"refs/tags/x\",\"object\":{\"sha\":\"6666\"}}");
        if (p.matches("/repos/[^/]+/[^/]+/tags")) return ok("[]");
        return new String[]{"404", "{\"message\":\"Not Found\",\"documentation_url\":\"https://docs.github.com/rest\"}"};
    }

    private static String[] ok(String json) {
        return new String[]{"200", json};
    }
}
