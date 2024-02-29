package io.kamsan.secureinvoices.filter;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.kamsan.secureinvoices.provider.TokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import static java.util.Arrays.asList;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthorizationFilter extends OncePerRequestFilter{
	
	private final TokenProvider tokenProvider;
	protected static final String TOKEN_KEY  = "token";
	protected static final String EMAIL_KEY  = "email";
	private static final String TOKEN_PREFIX = "Bearer ";
	private static final String AUTHORIZATION = "Authorization";
	private static final String[] PUBLIC_ROUTES = {"/user/register", "/user/login", "/user/verify/code"};

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filter)
			throws ServletException, IOException {
		
		try {
			Map<String,String> values = getRequestValues(request);
			String token = getToken(request);
			if (tokenProvider.isTokenValid(values.get(EMAIL_KEY), token)) {
				List<GrantedAuthority> authorities = tokenProvider.getAuthoritiesFromToken(values.get(TOKEN_KEY));
				Authentication authentication = tokenProvider.getAuthentication(values.get(EMAIL_KEY), authorities, request);
				// Set the subject authenticated with this email and with those authorities inside the security context 
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} else {
				SecurityContextHolder.clearContext();
			}
			
			// allow spring to continue the flow of the security chain
			filter.doFilter(request, response);
		} catch (Exception exception) {
			log.error(exception.getMessage());
			//processError(request, response, exception);
		}
	}
	
	private String getToken(HttpServletRequest request) {
		return ofNullable(request.getHeader("Authorization"))
				.filter(header -> header.startsWith(TOKEN_PREFIX))
				.map(token -> token.replace(TOKEN_PREFIX, EMPTY)).get();
	}

	private Map<String, String> getRequestValues(HttpServletRequest request) {
		return Map.of
			(
				EMAIL_KEY, tokenProvider.getSubject(getToken(request), request), 
				TOKEN_KEY, getToken(request)
			);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
		return request.getHeader(AUTHORIZATION) == null || !request.getHeader(AUTHORIZATION).startsWith(TOKEN_PREFIX)
				|| request.getMethod().equalsIgnoreCase("OPTIONS") || asList(PUBLIC_ROUTES).contains(request.getRequestURI());
	}

}
