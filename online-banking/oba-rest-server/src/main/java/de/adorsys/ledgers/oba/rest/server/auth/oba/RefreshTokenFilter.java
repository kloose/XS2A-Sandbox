package de.adorsys.ledgers.oba.rest.server.auth.oba;

import de.adorsys.ledgers.keycloak.client.api.KeycloakTokenService;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.util.WebUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

import static de.adorsys.ledgers.oba.rest.server.auth.oba.SecurityConstant.BEARER_TOKEN_PREFIX;

@RequiredArgsConstructor
public class RefreshTokenFilter extends AbstractAuthFilter {

    public static final String INVALID_REFRESH_TOKEN = "invalid refresh token";
    private final KeycloakTokenService tokenService;

    @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        String bearerToken = resolveBearerToken(request);
        try {
            if (StringUtils.isNotBlank(bearerToken) && isExpiredToken(bearerToken)) {
                BearerTokenTO bearerTokenTO = refreshAccessToken(request, response);
                refreshUserSession(bearerTokenTO, response, request.isSecure());
                HttpServletRequest newRequest = new RefreshTokenRequestWrapper(request, bearerTokenTO.getAccess_token());
                filterChain.doFilter(newRequest, response);
            } else {
                filterChain.doFilter(request, response);
            }
        } catch (FeignException | AccessDeniedException e) {
            handleAuthenticationFailure(response, e);
        }
    }


    private BearerTokenTO refreshAccessToken(HttpServletRequest request, HttpServletResponse response) {
        String bearerToken = resolveBearerToken(request);
        String jwtid = jwtId(bearerToken);
        String oldRefreshTokenCookieName = SecurityConstant.REFRESH_TOKEN_COOKIE_PREFIX + jwtid;
        String refreshToken = Optional.ofNullable(WebUtils.getCookie(request, oldRefreshTokenCookieName))
            .map(Cookie::getValue)
            .orElseThrow(() -> new AccessDeniedException(INVALID_REFRESH_TOKEN));
        BearerTokenTO bearerTokenTO = tokenService.refreshToken(refreshToken);
        removeCookie(response, oldRefreshTokenCookieName, request.isSecure());
        return bearerTokenTO;
    }


    private static class RefreshTokenRequestWrapper extends HttpServletRequestWrapper {
        private final String accessToken;

        RefreshTokenRequestWrapper(HttpServletRequest request, String accessToken) {
            super(request);
            this.accessToken = accessToken;
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.AUTHORIZATION.equals(name)) {
                return BEARER_TOKEN_PREFIX + accessToken;
            } else {
                return super.getHeader(name);
            }
        }
    }
}
