package com.example.ssl;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Map;

@RestController
public class AuthController {

    @Autowired
    private CasdoorProperties casdoor;

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        String authorizeUrl = casdoor.getEndpoint()
                + "/login/oauth/authorize"
                + "?client_id=" + casdoor.getClientId()
                + "&response_type=code"
                + "&redirect_uri=" + casdoor.getRedirectUri()
                + "&scope=openid profile email"
                + "&state=random_state_string";

        response.sendRedirect(authorizeUrl);
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String code,
                         HttpServletResponse response) throws IOException {

        RestTemplate restTemplate = createInsecureRestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("redirect_uri", casdoor.getRedirectUri());
        params.add("client_id", casdoor.getClientId());
        params.add("client_secret", casdoor.getClientSecret());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                casdoor.getEndpoint() + "/api/login/oauth/access_token",
                request,
                Map.class
        );

        if (tokenResponse.getStatusCode().is2xxSuccessful()) {
            String accessToken = (String) tokenResponse.getBody().get("access_token");
            if (accessToken != null) {
                Cookie cookie = new Cookie("access_token", accessToken);
                cookie.setPath("/");
                cookie.setHttpOnly(true);
                response.addCookie(cookie);
            }
        }

        response.sendRedirect("/index.html");
    }

    @GetMapping("/user-info")
    public ResponseEntity<?> userInfo(HttpServletRequest request) {

        String token = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Unauthorized — токен відсутній"));
        }

        RestTemplate restTemplate = createInsecureRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> userInfoResponse = restTemplate.exchange(
                    casdoor.getEndpoint() + "/api/userinfo",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            Map body = userInfoResponse.getBody();
            
            if (body != null && "error".equals(body.get("status"))) {
                return ResponseEntity.status(401)
                        .body(Map.of("error", "Unauthorized — токен невалідний"));
            }
            
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Unauthorized — токен невалідний"));
        }
    }

    private RestTemplate createInsecureRestTemplate() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            return new RestTemplate();
        } catch (Exception e) {
            return new RestTemplate();
        }
    }
}