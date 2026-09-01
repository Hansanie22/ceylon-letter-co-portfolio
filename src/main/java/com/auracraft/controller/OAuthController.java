package com.auracraft.controller;

import com.auracraft.entity.User;
import com.auracraft.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OAuthController – migrated from OAuthServlet.
 * Handles /api/oauth/google/login and /api/oauth/google/callback.
 *
 * Credentials are loaded from application.properties or environment variables.
 */
@Controller
@RequestMapping("/api/oauth")
public class OAuthController {

    private static final Logger LOG = Logger.getLogger(OAuthController.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private AuthService authService;

    @Autowired
    private com.auracraft.service.AuditLogService auditLogService;

    @Value("${oauth.google.client-id:}")
    private String googleClientId;

    @Value("${oauth.google.client-secret:}")
    private String googleClientSecret;

    // ── GET /api/oauth/google/login ──────────────────────────────────────────
    @GetMapping("/google/login")
    public void googleLogin(@RequestParam(required = false) String returnUrl,
                             HttpServletRequest request,
                             HttpServletResponse response) throws Exception {
        loadConfigIfNeeded();
        String baseUrl = getBaseUrl(request);
        String state = returnUrl != null ? URLEncoder.encode(returnUrl, StandardCharsets.UTF_8) : "";
        String redirectUri = baseUrl + "/api/oauth/google/callback";
        String authUrl = "https://accounts.google.com/o/oauth2/auth"
                + "?client_id=" + googleClientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=email%20profile"
                + "&state=" + state;
        response.sendRedirect(authUrl);
    }

    // ── GET /api/oauth/google/callback ───────────────────────────────────────
    @GetMapping("/google/callback")
    public void googleCallback(@RequestParam(required = false) String code,
                                @RequestParam(required = false) String state,
                                HttpServletRequest request,
                                HttpServletResponse response) throws Exception {
        loadConfigIfNeeded();
        String baseUrl = getBaseUrl(request);
        if (code == null) {
            response.sendRedirect(baseUrl + "/login.html?error=google_auth_failed");
            return;
        }
        try {
            String redirectUri = baseUrl + "/api/oauth/google/callback";
            JsonNode userInfo = getGoogleUserInfo(code, redirectUri);
            if (userInfo != null && userInfo.has("email")) {
                String email = userInfo.path("email").asText();
                String name = userInfo.path("name").asText("Google User");
                String picture = userInfo.has("picture") ? userInfo.path("picture").asText() : null;
                String providerId = userInfo.path("id").asText();
                loginOAuthUser(request, email, name, "GOOGLE", providerId, picture);
                User user = (User) request.getSession().getAttribute("loggedInUser");
                sendAuthSuccessScript(response, user, baseUrl, state);
            } else {
                response.sendRedirect(baseUrl + "/login.html?error=google_auth_failed");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "OAuth callback error", e);
            response.sendRedirect(baseUrl + "/login.html?error=oauth_exception");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private JsonNode getGoogleUserInfo(String code, String redirectUri) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String params = "client_id=" + googleClientId
                + "&client_secret=" + googleClientSecret
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(params))
                .build();
        HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode tokenJson = mapper.readTree(tokenResponse.body());
        String accessToken = tokenJson.path("access_token").asText(null);
        if (accessToken == null) return null;

        HttpRequest userRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/oauth2/v1/userinfo"))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        HttpResponse<String> userResponse = client.send(userRequest, HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(userResponse.body());
    }

    private void loginOAuthUser(HttpServletRequest request, String email, String name,
                                  String provider, String providerId, String pictureUrl) throws Exception {
        // Use AuthService to find or create the OAuth user
        User user = authService.loginOrRegisterOAuthUser(email, name, provider, providerId, pictureUrl);
        HttpSession session = request.getSession(true);
        session.setAttribute("loggedInUser", user);
        session.setAttribute("userId", user.getId());
        session.setAttribute("userEmail", user.getEmail());
        session.setAttribute("userRole", user.getRole());

        auditLogService.log(request, "OAUTH_LOGIN", "AUTH",
                "User signed in via Google OAuth (" + email + ")",
                "SUCCESS");
    }

    private void sendAuthSuccessScript(HttpServletResponse response, User user, String baseUrl, String state) throws Exception {
        String decodedState = (state != null && !state.isEmpty()) ? java.net.URLDecoder.decode(state, StandardCharsets.UTF_8) : "";
        String name = user != null ? user.getFullName() : "";
        String role = user != null ? user.getRole() : "CUSTOMER";
        String email = user != null ? user.getEmail() : "";
        Integer id = user != null ? user.getId() : 0;
        Boolean isSubscribed = user != null ? user.isSubscribed() : false;

        boolean isStaff = role != null && !"CUSTOMER".equalsIgnoreCase(role.trim());
        String returnUrl;
        if (isStaff) {
            returnUrl = baseUrl + "/admin.html";
        } else if (decodedState.isEmpty()) {
            returnUrl = baseUrl + "/account.html";
        } else if (decodedState.startsWith("http") || decodedState.startsWith("/")) {
            returnUrl = decodedState;
        } else {
            returnUrl = "/" + decodedState;
        }
        
        String html = """
            <!DOCTYPE html>
            <html><head><title>Login Successful</title></head>
            <body>
            <script>
              try {
                  var userObj = {
                    id: %d,
                    name: '%s',
                    email: '%s',
                    role: '%s',
                    isSubscribed: %b
                  };
                  localStorage.setItem('AuraCraft Studio_user', JSON.stringify(userObj));
                  sessionStorage.setItem('AuraCraft Studio_user', JSON.stringify(userObj));
              } catch(e) {
                  console.error(e);
              }
              setTimeout(function() {
                  window.location.href='%s';
              }, 50);
            </script>
            </body></html>
            """.formatted(id, esc(name), esc(email), esc(role), isSubscribed, esc(returnUrl));
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(html);
    }

    private void loadConfigIfNeeded() {
        if (googleClientId == null || googleClientId.isEmpty()) {
            googleClientId = System.getenv("GOOGLE_CLIENT_ID");
        }
        if (googleClientSecret == null || googleClientSecret.isEmpty()) {
            googleClientSecret = System.getenv("GOOGLE_CLIENT_SECRET");
        }
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("auracraft_oauth.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                if (googleClientId == null || googleClientId.isEmpty())
                    googleClientId = props.getProperty("GOOGLE_CLIENT_ID", "");
                if (googleClientSecret == null || googleClientSecret.isEmpty())
                    googleClientSecret = props.getProperty("GOOGLE_CLIENT_SECRET", "");
            }
        } catch (Exception ignored) {}
    }

    private String getBaseUrl(HttpServletRequest request) {
        String host = request.getServerName();
        if (host.contains("localhost") || host.contains("127.0.0.1")) {
            String scheme = request.getScheme();
            int port = request.getServerPort();
            String ctx = request.getContextPath();
            return scheme + "://" + host + (port != 80 && port != 443 ? ":" + port : "") + ctx;
        }
        return "https://auracraft.com";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("'", "\\'").replace("\\", "\\\\");
    }
}
