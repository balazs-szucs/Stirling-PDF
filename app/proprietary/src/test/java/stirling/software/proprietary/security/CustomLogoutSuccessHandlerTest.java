package stirling.software.proprietary.security;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import stirling.software.common.configuration.AppConfig;
import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.security.service.JwtServiceInterface;

@ExtendWith(MockitoExtension.class)
class CustomLogoutSuccessHandlerTest {

    @Mock private ApplicationProperties.Security securityProperties;

    @Mock private AppConfig appConfig;

    @Mock private JwtServiceInterface jwtService;

    @InjectMocks private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Test
    void testSuccessfulLogout() throws IOException {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        String token = "token";
        String logoutPath = "/login?logout=true";

        Mockito.when(response.isCommitted()).thenReturn(false);
        Mockito.when(jwtService.extractToken(request)).thenReturn(token);
        Mockito.doNothing().when(jwtService).clearToken(response);
        Mockito.when(request.getContextPath()).thenReturn("");
        Mockito.when(response.encodeRedirectURL(logoutPath)).thenReturn(logoutPath);

        customLogoutSuccessHandler.onLogoutSuccess(request, response, null);

        Mockito.verify(response).sendRedirect(logoutPath);
    }

    @Test
    void testSuccessfulLogoutViaJWT() throws IOException {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        String logoutPath = "/login?logout=true";
        String token = "token";

        Mockito.when(response.isCommitted()).thenReturn(false);
        Mockito.when(jwtService.extractToken(request)).thenReturn(token);
        Mockito.doNothing().when(jwtService).clearToken(response);
        Mockito.when(request.getContextPath()).thenReturn("");
        Mockito.when(response.encodeRedirectURL(logoutPath)).thenReturn(logoutPath);

        customLogoutSuccessHandler.onLogoutSuccess(request, response, null);

        Mockito.verify(response).sendRedirect(logoutPath);
        Mockito.verify(jwtService).clearToken(response);
    }

    @Test
    void testSuccessfulLogoutViaOAuth2() throws IOException {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        OAuth2AuthenticationToken oAuth2AuthenticationToken =
                Mockito.mock(OAuth2AuthenticationToken.class);
        ApplicationProperties.Security.OAUTH2 oauth =
                Mockito.mock(ApplicationProperties.Security.OAUTH2.class);

        Mockito.when(response.isCommitted()).thenReturn(false);
        Mockito.when(request.getParameter("oAuth2AuthenticationErrorWeb")).thenReturn(null);
        Mockito.when(request.getParameter("errorOAuth")).thenReturn(null);
        Mockito.when(request.getScheme()).thenReturn("http");
        Mockito.when(request.getServerName()).thenReturn("localhost");
        Mockito.when(request.getServerPort()).thenReturn(8080);
        Mockito.when(request.getContextPath()).thenReturn("");
        Mockito.when(securityProperties.getOauth2()).thenReturn(oauth);
        Mockito.when(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId())
                .thenReturn("test");

        customLogoutSuccessHandler.onLogoutSuccess(request, response, oAuth2AuthenticationToken);

        Mockito.verify(response).sendRedirect("http://localhost:8080/login?logout=true");
    }

    @Test
    void testUserIsDisabledRedirect() throws IOException {
        String error = "userIsDisabled";
        String url = "http://localhost:8080";
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        OAuth2AuthenticationToken authentication = Mockito.mock(OAuth2AuthenticationToken.class);
        ApplicationProperties.Security.OAUTH2 oauth =
                Mockito.mock(ApplicationProperties.Security.OAUTH2.class);

        Mockito.when(response.isCommitted()).thenReturn(false);
        Mockito.when(request.getParameter("oAuth2AuthenticationErrorWeb")).thenReturn(null);
        Mockito.when(request.getParameter("errorOAuth")).thenReturn(null);
        Mockito.when(request.getParameter("oAuth2AutoCreateDisabled")).thenReturn(null);
        Mockito.when(request.getParameter("oAuth2AdminBlockedUser")).thenReturn(null);
        Mockito.when(request.getParameter(error)).thenReturn("true");
        Mockito.when(request.getScheme()).thenReturn("http");
        Mockito.when(request.getServerName()).thenReturn("localhost");
        Mockito.when(request.getServerPort()).thenReturn(8080);
        Mockito.when(request.getContextPath()).thenReturn("");
        Mockito.when(securityProperties.getOauth2()).thenReturn(oauth);
        Mockito.when(authentication.getAuthorizedClientRegistrationId()).thenReturn("test");

        customLogoutSuccessHandler.onLogoutSuccess(request, response, authentication);

        Mockito.verify(response).sendRedirect(url + "/login?errorOAuth=" + error);
    }

    @Test
    void testUserAlreadyExistsWebRedirect() throws IOException {
        String error = "oAuth2AuthenticationErrorWeb";
        String errorPath = "userAlreadyExistsWeb";
        String url = "http://localhost:8080";
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        OAuth2AuthenticationToken authentication = Mockito.mock(OAuth2AuthenticationToken.class);
        ApplicationProperties.Security.OAUTH2 oauth =
                Mockito.mock(ApplicationProperties.Security.OAUTH2.class);

        Mockito.when(response.isCommitted()).thenReturn(false);
        Mockito.when(request.getParameter(error)).thenReturn("true");
        Mockito.when(request.getScheme()).thenReturn("http");
        Mockito.when(request.getServerName()).thenReturn("localhost");
        Mockito.when(request.getServerPort()).thenReturn(8080);
        Mockito.when(request.getContextPath()).thenReturn("");
        Mockito.when(securityProperties.getOauth2()).thenReturn(oauth);
        Mockito.when(authentication.getAuthorizedClientRegistrationId()).thenReturn("test");

        customLogoutSuccessHandler.onLogoutSuccess(request, response, authentication);

        Mockito.verify(response).sendRedirect(url + "/login?errorOAuth=" + errorPath);
    }

    @Test
    void testErrorOAuthRedirect() throws IOException {
        String error = "testError";
        String url = "http://localhost:8080";
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        OAuth2AuthenticationToken authentication = Mockito.mock(OAuth2AuthenticationToken.class);
        ApplicationProperties.Security.OAUTH2 oauth =
                Mockito.mock(ApplicationProperties.Security.OAUTH2.class);

        Mockito.when(response.isCommitted()).thenReturn(false);
        Mockito.when(request.getParameter("oAuth2AuthenticationErrorWeb")).thenReturn(null);
        Mockito.when(request.getParameter("errorOAuth")).thenReturn("!!!" + error + "!!!");
        Mockito.when(request.getScheme()).thenReturn("http");
        Mockito.when(request.getServerName()).thenReturn("localhost");
        Mockito.when(request.getServerPort()).thenReturn(8080);
        Mockito.when(request.getContextPath()).thenReturn("");
        Mockito.when(securityProperties.getOauth2()).thenReturn(oauth);
        Mockito.when(authentication.getAuthorizedClientRegistrationId()).thenReturn("test");

        customLogoutSuccessHandler.onLogoutSuccess(request, response, authentication);

        Mockito.verify(response).sendRedirect(url + "/login?errorOAuth=" + error);
    }

    @Test
    void testOAuth2AutoCreateDisabled() throws IOException {
        String error = "oAuth2AutoCreateDisabled";
        String url = "http://localhost:8080";
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        OAuth2AuthenticationToken authentication = Mockito.mock(OAuth2AuthenticationToken.class);
        ApplicationProperties.Security.OAUTH2 oauth =
                Mockito.mock(ApplicationProperties.Security.OAUTH2.class);

        Mockito.when(response.isCommitted()).thenReturn(false);
        Mockito.when(request.getParameter("oAuth2AuthenticationErrorWeb")).thenReturn(null);
        Mockito.when(request.getParameter("errorOAuth")).thenReturn(null);
        Mockito.when(request.getParameter(error)).thenReturn("true");
        Mockito.when(request.getContextPath()).thenReturn(url);
        Mockito.when(request.getScheme()).thenReturn("http");
        Mockito.when(request.getServerName()).thenReturn("localhost");
        Mockito.when(request.getServerPort()).thenReturn(8080);
        Mockito.when(request.getContextPath()).thenReturn("");
        Mockito.when(securityProperties.getOauth2()).thenReturn(oauth);
        Mockito.when(authentication.getAuthorizedClientRegistrationId()).thenReturn("test");

        customLogoutSuccessHandler.onLogoutSuccess(request, response, authentication);

        Mockito.verify(response).sendRedirect(url + "/login?errorOAuth=" + error);
    }

    @Test
    void testOAuth2Error() throws IOException {
        String error = "test";
        String url = "http://localhost:8080";
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        OAuth2AuthenticationToken authentication = Mockito.mock(OAuth2AuthenticationToken.class);
        ApplicationProperties.Security.OAUTH2 oauth =
                Mockito.mock(ApplicationProperties.Security.OAUTH2.class);

        Mockito.when(response.isCommitted()).thenReturn(false);
        Mockito.when(request.getParameter("oAuth2AuthenticationErrorWeb")).thenReturn(null);
        Mockito.when(request.getParameter("errorOAuth")).thenReturn(null);
        Mockito.when(request.getParameter("oAuth2AutoCreateDisabled")).thenReturn(null);
        Mockito.when(request.getParameter("oAuth2AdminBlockedUser")).thenReturn(null);
        Mockito.when(request.getParameter("userIsDisabled")).thenReturn(null);
        Mockito.when(request.getParameter("error")).thenReturn("!@$!@£" + error + "£$%^*$");
        Mockito.when(request.getScheme()).thenReturn("http");
        Mockito.when(request.getServerName()).thenReturn("localhost");
        Mockito.when(request.getServerPort()).thenReturn(8080);
        Mockito.when(request.getContextPath()).thenReturn("");
        Mockito.when(securityProperties.getOauth2()).thenReturn(oauth);
        Mockito.when(authentication.getAuthorizedClientRegistrationId()).thenReturn("test");

        customLogoutSuccessHandler.onLogoutSuccess(request, response, authentication);

        Mockito.verify(response).sendRedirect(url + "/login?errorOAuth=" + error);
    }

    @Test
    void testOAuth2BadCredentialsError() throws IOException {
        String error = "badCredentials";
        String url = "http://localhost:8080";
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        OAuth2AuthenticationToken authentication = Mockito.mock(OAuth2AuthenticationToken.class);
        ApplicationProperties.Security.OAUTH2 oauth =
                Mockito.mock(ApplicationProperties.Security.OAUTH2.class);

        Mockito.when(response.isCommitted()).thenReturn(false);
        Mockito.when(request.getParameter("oAuth2AuthenticationErrorWeb")).thenReturn(null);
        Mockito.when(request.getParameter("errorOAuth")).thenReturn(null);
        Mockito.when(request.getParameter("oAuth2AutoCreateDisabled")).thenReturn(null);
        Mockito.when(request.getParameter("oAuth2AdminBlockedUser")).thenReturn(null);
        Mockito.when(request.getParameter("userIsDisabled")).thenReturn(null);
        Mockito.when(request.getParameter("error")).thenReturn(null);
        Mockito.when(request.getParameter(error)).thenReturn("true");
        Mockito.when(request.getScheme()).thenReturn("http");
        Mockito.when(request.getServerName()).thenReturn("localhost");
        Mockito.when(request.getServerPort()).thenReturn(8080);
        Mockito.when(request.getContextPath()).thenReturn("");
        Mockito.when(securityProperties.getOauth2()).thenReturn(oauth);
        Mockito.when(authentication.getAuthorizedClientRegistrationId()).thenReturn("test");

        customLogoutSuccessHandler.onLogoutSuccess(request, response, authentication);

        Mockito.verify(response).sendRedirect(url + "/login?errorOAuth=" + error);
    }

    @Test
    void testOAuth2AdminBlockedUser() throws IOException {
        String error = "oAuth2AdminBlockedUser";
        String url = "http://localhost:8080";
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        OAuth2AuthenticationToken authentication = Mockito.mock(OAuth2AuthenticationToken.class);
        ApplicationProperties.Security.OAUTH2 oauth =
                Mockito.mock(ApplicationProperties.Security.OAUTH2.class);

        Mockito.when(response.isCommitted()).thenReturn(false);
        Mockito.when(request.getParameter("oAuth2AuthenticationErrorWeb")).thenReturn(null);
        Mockito.when(request.getParameter("errorOAuth")).thenReturn(null);
        Mockito.when(request.getParameter("oAuth2AutoCreateDisabled")).thenReturn(null);
        Mockito.when(request.getParameter(error)).thenReturn("true");
        Mockito.when(request.getScheme()).thenReturn("http");
        Mockito.when(request.getServerName()).thenReturn("localhost");
        Mockito.when(request.getServerPort()).thenReturn(8080);
        Mockito.when(request.getContextPath()).thenReturn("");
        Mockito.when(securityProperties.getOauth2()).thenReturn(oauth);
        Mockito.when(authentication.getAuthorizedClientRegistrationId()).thenReturn("test");

        customLogoutSuccessHandler.onLogoutSuccess(request, response, authentication);

        Mockito.verify(response).sendRedirect(url + "/login?errorOAuth=" + error);
    }
}
