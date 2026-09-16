package com.auth.center.oauth;

import com.auth.center.cas.ServiceTicket;
import com.auth.center.cas.TicketGrantingTicket;
import com.auth.center.cas.TicketRegistry;
import com.auth.center.security.JwtService;
import com.auth.center.security.SystemPermissionResolver;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** OAuth 2.1 Authorization Server endpoints used by MCP clients. */
@RestController
@RequestMapping("/oauth2")
public class OAuthAuthorizationController {

    private static final Logger log = LoggerFactory.getLogger(OAuthAuthorizationController.class);

    private static final String TGC_COOKIE = "CASTGC";
    private static final String AUTHORIZATION_CODE = "code";
    private static final String PKCE_S256 = "S256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final OAuthSettingsService settings;
    private final OAuthClientService clients;
    private final OAuthStateService oauthState;
    private final TicketRegistry ticketRegistry;
    private final JwtService jwtService;
    private final SystemPermissionResolver permissions;
    private final String publicBaseUrl;

    public OAuthAuthorizationController(
            OAuthSettingsService settings,
            OAuthClientService clients,
            OAuthStateService state,
            TicketRegistry ticketRegistry,
            JwtService jwtService,
            SystemPermissionResolver permissions,
            @org.springframework.beans.factory.annotation.Value("${auth.cas.public-base-url:}")
                    String publicBaseUrl) {
        this.settings = settings;
        this.clients = clients;
        this.oauthState = state;
        this.ticketRegistry = ticketRegistry;
        this.jwtService = jwtService;
        this.permissions = permissions;
        this.publicBaseUrl = publicBaseUrl;
    }

    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(
            @RequestParam String response_type,
            @RequestParam String client_id,
            @RequestParam String redirect_uri,
            @RequestParam String scope,
            @RequestParam(required = false) String state,
            @RequestParam String code_challenge,
            @RequestParam String code_challenge_method,
            @RequestParam(required = false) String resource,
            @CookieValue(name = TGC_COOKIE, required = false) String tgc) {
        OAuthClient client = clients.findEnabled(client_id);
        if (!AUTHORIZATION_CODE.equals(response_type)
                || client == null
                || !clients.redirectUris(client).contains(redirect_uri)
                || !PKCE_S256.equals(code_challenge_method)
                || code_challenge == null
                || code_challenge.isBlank()) {
            return oauthError("invalid_request", "The authorization request is invalid");
        }
        OAuthSettingsService.Snapshot current = settings.current();
        String requestedResource =
                resource == null || resource.isBlank() ? current.mcpResourceUrl() : resource;
        List<String> requestedScopes = splitScopes(scope);
        if (!requestedResource.equals(client.getResourceAudience())
                || requestedScopes.isEmpty()
                || !clients.scopes(client).containsAll(requestedScopes)) {
            return oauthError("invalid_scope", "The requested resource or scope is not allowed");
        }
        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("clientId", client.getClientId());
        tx.put("redirectUri", redirect_uri);
        tx.put("scope", String.join(" ", requestedScopes));
        tx.put("resource", requestedResource);
        tx.put("codeChallenge", code_challenge);
        tx.put("codeChallengeMethod", code_challenge_method);
        tx.put("state", state);
        tx.put("createdAt", Instant.now().getEpochSecond());

        TicketGrantingTicket tgt = tgc == null ? null : ticketRegistry.getTgt(tgc);
        if (tgt != null && !tgt.isExpired())
            return issueCodeAndRedirect(tx, tgt.getUserId(), tgt.getUsername());

        String transactionId =
                oauthState.createTransaction(tx, current.authorizationCodeTtlSeconds());
        String callback = callbackUrl() + "?tx=" + url(transactionId);
        String login =
                UriComponentsBuilder.fromPath("/cas/login")
                        .queryParam("service", callback)
                        .toUriString();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, login).build();
    }

    @GetMapping("/authorize/cas-callback")
    public ResponseEntity<?> authorizationCallback(
            @RequestParam String tx, @RequestParam String ticket) {
        Map<String, Object> request = oauthState.consumeTransaction(tx);
        if (request == null)
            return oauthError("invalid_request", "The authorization transaction expired");
        ServiceTicket validated =
                ticketRegistry.validateSt(ticket, callbackUrl() + "?tx=" + url(tx));
        if (validated == null) return oauthError("access_denied", "Authentication failed");
        return issueCodeAndRedirect(request, validated.getUserId(), validated.getUsername());
    }

    @PostMapping(
            value = "/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam String grant_type,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String redirect_uri,
            @RequestParam(required = false) String client_id,
            @RequestParam(required = false) String client_secret,
            @RequestParam(required = false) String code_verifier,
            @RequestParam(required = false) String refresh_token,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                    String authorization) {
        ClientCredentials credentials = credentials(client_id, client_secret, authorization);
        OAuthClient client = clients.findEnabled(credentials.clientId());
        if (client == null
                || (!"public".equals(client.getClientType())
                        && !clients.authenticateSecret(client, credentials.secret()))) {
            return tokenError("invalid_client");
        }
        if ("authorization_code".equals(grant_type)) {
            return exchangeCode(client, code, redirect_uri, code_verifier);
        }
        if ("refresh_token".equals(grant_type)) {
            return exchangeRefresh(client, refresh_token);
        }
        return tokenError("unsupported_grant_type");
    }

    @PostMapping(value = "/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> revoke(
            @RequestParam String token,
            @RequestParam(required = false) String client_id,
            @RequestParam(required = false) String client_secret,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                    String authorization) {
        ClientCredentials credentials = credentials(client_id, client_secret, authorization);
        OAuthClient client = clients.findEnabled(credentials.clientId());
        if (client == null
                || (!"public".equals(client.getClientType())
                        && !clients.authenticateSecret(client, credentials.secret()))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Map<String, Object> refresh = oauthState.consumeRefreshToken(token);
        if (refresh != null && client.getClientId().equals(refresh.get("clientId")))
            return ResponseEntity.ok().build();
        try {
            var claims = jwtService.parseToken(token);
            if (client.getClientId().equals(claims.get("client_id", String.class)))
                return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            // RFC 7009 requires revocation to be idempotent for an unknown token.
            log.debug(
                    "OAuth revoke received an opaque or invalid token: {}",
                    e.getClass().getSimpleName());
        }
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<?> issueCodeAndRedirect(
            Map<String, Object> tx, Long userId, String username) {
        if (userId == null || username == null || username.isBlank())
            return oauthError("access_denied", "The user is not available");
        tx.put("userId", userId);
        tx.put("username", username);
        SystemPermissionResolver.Resolved resolved = permissions.resolve(userId);
        tx.put("permissionSet", resolved.getLegacyPermissionSet());
        tx.put("capabilities", resolved.getLegacyCapabilities());
        tx.put("systemPermissions", resolved.getSystemPermissions());
        String code =
                oauthState.createAuthorizationCode(
                        tx, settings.current().authorizationCodeTtlSeconds());
        String redirect =
                UriComponentsBuilder.fromUriString((String) tx.get("redirectUri"))
                        .queryParam("code", code)
                        .queryParam("state", tx.get("state"))
                        .build(true)
                        .toUriString();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirect)
                .build();
    }

    private ResponseEntity<Map<String, Object>> exchangeCode(
            OAuthClient client, String code, String redirectUri, String verifier) {
        Map<String, Object> payload = oauthState.consumeAuthorizationCode(code);
        if (payload == null
                || !client.getClientId().equals(payload.get("clientId"))
                || !redirectUriEquals(redirectUri, payload.get("redirectUri"))
                || !verifyPkce(verifier, (String) payload.get("codeChallenge")))
            return tokenError("invalid_grant");
        return issueTokens(client, payload);
    }

    private ResponseEntity<Map<String, Object>> exchangeRefresh(
            OAuthClient client, String rawToken) {
        Map<String, Object> payload = oauthState.consumeRefreshToken(rawToken);
        if (payload == null || !client.getClientId().equals(payload.get("clientId")))
            return tokenError("invalid_grant");
        return issueTokens(client, payload);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> issueTokens(
            OAuthClient client, Map<String, Object> payload) {
        int ttl = client.getTokenTtlSeconds();
        OAuthSettingsService.Snapshot current = settings.current();
        String access =
                jwtService.generateOAuthAccessToken(
                        ((Number) payload.get("userId")).longValue(),
                        (String) payload.get("username"),
                        (String) payload.get("permissionSet"),
                        (String) payload.get("capabilities"),
                        (Map<String, Object>) payload.get("systemPermissions"),
                        current.issuer(),
                        client.getResourceAudience(),
                        client.getClientId(),
                        (String) payload.get("scope"),
                        ttl * 1000L);
        Map<String, Object> refreshPayload = new LinkedHashMap<>(payload);
        refreshPayload.put("clientId", client.getClientId());
        String refresh =
                oauthState.createRefreshToken(refreshPayload, current.refreshTokenTtlSeconds());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", access);
        body.put("token_type", "Bearer");
        body.put("expires_in", ttl);
        body.put("refresh_token", refresh);
        body.put("scope", payload.get("scope"));
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(body);
    }

    private ClientCredentials credentials(String clientId, String secret, String authorization) {
        if (authorization != null && authorization.startsWith("Basic ")) {
            try {
                String decoded =
                        new String(
                                Base64.getDecoder().decode(authorization.substring(6)),
                                StandardCharsets.UTF_8);
                int separator = decoded.indexOf(':');
                if (separator > 0)
                    return new ClientCredentials(
                            decoded.substring(0, separator), decoded.substring(separator + 1));
            } catch (IllegalArgumentException e) {
                // handled as invalid_client below
                log.debug(
                        "OAuth client Basic credentials are not valid Base64: {}",
                        e.getClass().getSimpleName());
            }
        }
        return new ClientCredentials(clientId, secret);
    }

    private ResponseEntity<Map<String, Object>> oauthError(String error, String description) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", error, "error_description", description));
    }

    private ResponseEntity<Map<String, Object>> tokenError(String error) {
        HttpStatus status =
                "invalid_client".equals(error) ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", error));
    }

    private String callbackUrl() {
        String base =
                publicBaseUrl == null || publicBaseUrl.isBlank()
                        ? settings.current().issuer()
                        : publicBaseUrl;
        return base.replaceAll("/$", "") + "/oauth2/authorize/cas-callback";
    }

    private static List<String> splitScopes(String value) {
        if (value == null) return List.of();
        List<String> scopes = new ArrayList<>();
        for (String scope : value.trim().split("\\s+"))
            if (!scope.isBlank() && !scopes.contains(scope)) scopes.add(scope);
        return scopes;
    }

    private static boolean redirectUriEquals(String actual, Object expected) {
        return actual != null && actual.equals(expected);
    }

    private static boolean verifyPkce(String verifier, String challenge) {
        if (verifier == null
                || challenge == null
                || verifier.length() < 43
                || verifier.length() > 128) return false;
        try {
            String calculated =
                    URL_ENCODER.encodeToString(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            return MessageDigest.isEqual(
                    calculated.getBytes(StandardCharsets.US_ASCII),
                    challenge.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.NoSuchAlgorithmException e) {
            return false;
        }
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record ClientCredentials(String clientId, String secret) {}
}
