package de.adorsys.psd2.sandbox.tpp.rest.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTParser;
import de.adorsys.ledgers.middleware.api.domain.um.AccessTokenTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.psd2.sandbox.tpp.rest.server.exception.ErrorResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.adorsys.psd2.sandbox.tpp.rest.server.auth.SecurityConstant.ACCESS_TOKEN;
import static de.adorsys.psd2.sandbox.tpp.rest.server.auth.SecurityConstant.AUTHORIZATION_HEADER;
import static de.adorsys.psd2.sandbox.tpp.rest.server.auth.SecurityConstant.BEARER_TOKEN_PREFIX;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Slf4j
abstract class AbstractAuthFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected void handleAuthenticationFailure(HttpServletResponse response, Exception e) throws IOException {
        log.error(e.getMessage());

        Map<String, String> data = new ErrorResponse()
            .buildContent(UNAUTHORIZED.value(), UNAUTHORIZED.getReasonPhrase());
        response.setStatus(UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().println(objectMapper.writeValueAsString(data));
    }

    protected String obtainFromHeader(HttpServletRequest request, String headerKey) {
        return request.getHeader(headerKey);
    }

    protected boolean authenticationIsRequired() {
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        return isNotAuthenticated(existingAuth) || isNotMiddlewareAuthentication(existingAuth);
    }

    protected void fillSecurityContext(BearerTokenTO token) {
        SecurityContextHolder.getContext()
            .setAuthentication(new MiddlewareAuthentication(token.getAccessTokenObject(), token, buildGrantedAuthorities(token.getAccessTokenObject())));
    }

    protected String resolveBearerToken(HttpServletRequest request) {
        return Optional.ofNullable(obtainFromHeader(request, AUTHORIZATION_HEADER))
            .filter(StringUtils::isNotBlank)
            .filter(t -> StringUtils.startsWithIgnoreCase(t, BEARER_TOKEN_PREFIX))
            .map(t -> StringUtils.substringAfter(t, BEARER_TOKEN_PREFIX))
            .orElse(null);
    }


    private boolean isNotAuthenticated(Authentication existingAuth) {
        return existingAuth == null || !existingAuth.isAuthenticated();
    }

    private boolean isNotMiddlewareAuthentication(Authentication existingAuth) {
        return !(existingAuth instanceof MiddlewareAuthentication);
    }

    private List<GrantedAuthority> buildGrantedAuthorities(AccessTokenTO accessTokenTO) {
        return accessTokenTO.getRole() != null
            ? Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + accessTokenTO.getRole().name()))
            : Collections.emptyList();
    }


    protected void removeCookie(HttpServletResponse response, String cookieName, boolean isSecure) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(isSecure);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    protected void addRefreshTokenCookie(HttpServletResponse response, String jwtId, String value, boolean isSecure) {
        String cookieName = SecurityConstant.REFRESH_TOKEN_COOKIE_PREFIX + jwtId;
        Cookie cookie = new Cookie(cookieName, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(isSecure);
        cookie.setPath("/");
        response.addCookie(cookie);
    }


    protected void refreshUserSession(BearerTokenTO bearerTokenTO, HttpServletResponse response, boolean isSecure) {
        String access_token = bearerTokenTO.getAccess_token();
        addRefreshTokenCookie(response, jwtId(access_token), bearerTokenTO.getRefresh_token(), isSecure);
        addBearerTokenHeader(access_token, response);
    }

    protected void addBearerTokenHeader(String token, HttpServletResponse response) {
        response.setHeader(ACCESS_TOKEN, token);
    }

    @SneakyThrows
    protected String jwtId(String jwtToken) {
        return StringUtils.isNotEmpty(jwtToken) ?
            JWTParser.parse(jwtToken).getJWTClaimsSet().getJWTID() :
            null;
    }

    @SneakyThrows
    protected boolean isExpiredToken(String jwtToken) {
        Date expirationTime = JWTParser.parse(jwtToken).getJWTClaimsSet().getExpirationTime();
        return Optional.ofNullable(expirationTime)
            .map(d -> d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
            .map(d -> d.isBefore(LocalDateTime.now()))
            .orElse(true);
    }

}
