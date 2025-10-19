package stirling.software.proprietary.security.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.security.model.exception.AuthenticationFailureException;
import stirling.software.proprietary.security.service.CustomUserDetailsService;
import stirling.software.proprietary.security.service.JwtServiceInterface;
import stirling.software.proprietary.security.service.UserService;

@Disabled
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtServiceInterface jwtService;

    @Mock private CustomUserDetailsService userDetailsService;

    @Mock private UserService userService;

    @Mock private ApplicationProperties.Security securityProperties;

    @Mock private HttpServletRequest request;

    @Mock private HttpServletResponse response;

    @Mock private FilterChain filterChain;

    @Mock private UserDetails userDetails;

    @Mock private SecurityContext securityContext;

    @Mock private AuthenticationEntryPoint authenticationEntryPoint;

    @InjectMocks private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void shouldNotAuthenticateWhenJwtDisabled() throws ServletException, IOException {
        Mockito.when(jwtService.isJwtEnabled()).thenReturn(false);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Mockito.verify(filterChain).doFilter(request, response);
        Mockito.verify(jwtService, Mockito.never()).extractToken(ArgumentMatchers.any());
    }

    @Test
    void shouldNotFilterWhenPageIsLogin() throws ServletException, IOException {
        Mockito.when(jwtService.isJwtEnabled()).thenReturn(true);
        Mockito.when(request.getRequestURI()).thenReturn("/login");
        Mockito.when(request.getContextPath()).thenReturn("/login");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Mockito.verify(filterChain, Mockito.never()).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal() throws ServletException, IOException {
        String token = "valid-jwt-token";
        String newToken = "new-jwt-token";
        String username = "testuser";
        Map<String, Object> claims = Map.of("sub", username, "authType", "WEB");

        Mockito.when(jwtService.isJwtEnabled()).thenReturn(true);
        Mockito.when(request.getContextPath()).thenReturn("/");
        Mockito.when(request.getRequestURI()).thenReturn("/protected");
        Mockito.when(jwtService.extractToken(request)).thenReturn(token);
        Mockito.doNothing().when(jwtService).validateToken(token);
        Mockito.when(jwtService.extractClaims(token)).thenReturn(claims);
        Mockito.when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());
        Mockito.when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

        try (MockedStatic<SecurityContextHolder> mockedSecurityContextHolder =
                Mockito.mockStatic(SecurityContextHolder.class)) {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

            Mockito.when(securityContext.getAuthentication())
                    .thenReturn(null)
                    .thenReturn(authToken);
            mockedSecurityContextHolder
                    .when(SecurityContextHolder::getContext)
                    .thenReturn(securityContext);
            Mockito.when(
                            jwtService.generateToken(
                                    ArgumentMatchers.any(UsernamePasswordAuthenticationToken.class),
                                    ArgumentMatchers.eq(claims)))
                    .thenReturn(newToken);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            Mockito.verify(jwtService).validateToken(token);
            Mockito.verify(jwtService).extractClaims(token);
            Mockito.verify(userDetailsService).loadUserByUsername(username);
            Mockito.verify(securityContext)
                    .setAuthentication(
                            ArgumentMatchers.any(UsernamePasswordAuthenticationToken.class));
            Mockito.verify(jwtService)
                    .generateToken(
                            ArgumentMatchers.any(UsernamePasswordAuthenticationToken.class),
                            ArgumentMatchers.eq(claims));
            Mockito.verify(jwtService).addToken(response, newToken);
            Mockito.verify(filterChain).doFilter(request, response);
        }
    }

    @Test
    void testDoFilterInternalWithMissingTokenForRootPath() throws ServletException, IOException {
        Mockito.when(jwtService.isJwtEnabled()).thenReturn(true);
        Mockito.when(request.getRequestURI()).thenReturn("/");
        Mockito.when(request.getMethod()).thenReturn("GET");
        Mockito.when(jwtService.extractToken(request)).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Mockito.verify(response).sendRedirect("/login");
        Mockito.verify(filterChain, Mockito.never()).doFilter(request, response);
    }

    @Test
    void validationFailsWithInvalidToken() throws ServletException, IOException {
        String token = "invalid-jwt-token";

        Mockito.when(jwtService.isJwtEnabled()).thenReturn(true);
        Mockito.when(request.getRequestURI()).thenReturn("/protected");
        Mockito.when(request.getContextPath()).thenReturn("/");
        Mockito.when(jwtService.extractToken(request)).thenReturn(token);
        Mockito.doThrow(new AuthenticationFailureException("Invalid token"))
                .when(jwtService)
                .validateToken(token);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Mockito.verify(jwtService).validateToken(token);
        Mockito.verify(authenticationEntryPoint)
                .commence(
                        ArgumentMatchers.eq(request),
                        ArgumentMatchers.eq(response),
                        ArgumentMatchers.any(AuthenticationFailureException.class));
        Mockito.verify(filterChain, Mockito.never()).doFilter(request, response);
    }

    @Test
    void validationFailsWithExpiredToken() throws ServletException, IOException {
        String token = "expired-jwt-token";

        Mockito.when(jwtService.isJwtEnabled()).thenReturn(true);
        Mockito.when(request.getRequestURI()).thenReturn("/protected");
        Mockito.when(request.getContextPath()).thenReturn("/");
        Mockito.when(jwtService.extractToken(request)).thenReturn(token);
        Mockito.doThrow(new AuthenticationFailureException("The token has expired"))
                .when(jwtService)
                .validateToken(token);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Mockito.verify(jwtService).validateToken(token);
        Mockito.verify(authenticationEntryPoint)
                .commence(
                        ArgumentMatchers.eq(request),
                        ArgumentMatchers.eq(response),
                        ArgumentMatchers.any());
        Mockito.verify(filterChain, Mockito.never()).doFilter(request, response);
    }

    @Test
    void exceptionThrown_WhenUserNotFound() throws ServletException, IOException {
        String token = "valid-jwt-token";
        String username = "nonexistentuser";
        Map<String, Object> claims = Map.of("sub", username, "authType", "WEB");

        Mockito.when(jwtService.isJwtEnabled()).thenReturn(true);
        Mockito.when(request.getRequestURI()).thenReturn("/protected");
        Mockito.when(request.getContextPath()).thenReturn("/");
        Mockito.when(jwtService.extractToken(request)).thenReturn(token);
        Mockito.doNothing().when(jwtService).validateToken(token);
        Mockito.when(jwtService.extractClaims(token)).thenReturn(claims);
        Mockito.when(userDetailsService.loadUserByUsername(username)).thenReturn(null);

        try (MockedStatic<SecurityContextHolder> mockedSecurityContextHolder =
                Mockito.mockStatic(SecurityContextHolder.class)) {
            Mockito.when(securityContext.getAuthentication()).thenReturn(null);
            mockedSecurityContextHolder
                    .when(SecurityContextHolder::getContext)
                    .thenReturn(securityContext);

            UsernameNotFoundException result =
                    Assertions.assertThrows(
                            UsernameNotFoundException.class,
                            () ->
                                    jwtAuthenticationFilter.doFilterInternal(
                                            request, response, filterChain));

            Assertions.assertEquals("User not found: " + username, result.getMessage());
            Mockito.verify(userDetailsService).loadUserByUsername(username);
            Mockito.verify(filterChain, Mockito.never()).doFilter(request, response);
        }
    }

    @Test
    void testAuthenticationEntryPointCalledWithCorrectException()
            throws ServletException, IOException {
        Mockito.when(jwtService.isJwtEnabled()).thenReturn(true);
        Mockito.when(request.getRequestURI()).thenReturn("/protected");
        Mockito.when(request.getContextPath()).thenReturn("/");
        Mockito.when(jwtService.extractToken(request)).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        Mockito.verify(authenticationEntryPoint)
                .commence(
                        ArgumentMatchers.eq(request),
                        ArgumentMatchers.eq(response),
                        ArgumentMatchers.argThat(
                                exception ->
                                        exception
                                                .getMessage()
                                                .equals("JWT is missing from the request")));
        Mockito.verify(filterChain, Mockito.never()).doFilter(request, response);
    }
}
