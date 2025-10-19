package stirling.software.proprietary.security.saml2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.saml2.provider.service.authentication.Saml2PostAuthenticationRequest;
import org.springframework.security.saml2.provider.service.registration.AssertingPartyMetadata;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import stirling.software.proprietary.security.service.JwtServiceInterface;

@ExtendWith(MockitoExtension.class)
class JwtSaml2AuthenticationRequestRepositoryTest {

    private static final String SAML_REQUEST_TOKEN = "stirling_saml_request_token";

    private Map<String, String> tokenStore;

    @Mock private JwtServiceInterface jwtService;

    @Mock private RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    private JwtSaml2AuthenticationRequestRepository jwtSaml2AuthenticationRequestRepository;

    @BeforeEach
    void setUp() {
        tokenStore = new ConcurrentHashMap<>();
        jwtSaml2AuthenticationRequestRepository =
                new JwtSaml2AuthenticationRequestRepository(
                        tokenStore, jwtService, relyingPartyRegistrationRepository);
    }

    @Test
    void saveAuthenticationRequest() {
        var authRequest = Mockito.mock(Saml2PostAuthenticationRequest.class);
        var request = Mockito.mock(MockHttpServletRequest.class);
        var response = Mockito.mock(MockHttpServletResponse.class);
        String token = "testToken";
        String id = "testId";
        String relayState = "testRelayState";
        String authnRequestUri = "example.com/authnRequest";
        Map<String, Object> claims = Map.of();
        String samlRequest = "testSamlRequest";
        String relyingPartyRegistrationId = "stirling-pdf";

        Mockito.when(jwtService.isJwtEnabled()).thenReturn(true);
        Mockito.when(authRequest.getRelayState()).thenReturn(relayState);
        Mockito.when(authRequest.getId()).thenReturn(id);
        Mockito.when(authRequest.getAuthenticationRequestUri()).thenReturn(authnRequestUri);
        Mockito.when(authRequest.getSamlRequest()).thenReturn(samlRequest);
        Mockito.when(authRequest.getRelyingPartyRegistrationId())
                .thenReturn(relyingPartyRegistrationId);
        Mockito.when(jwtService.generateToken(ArgumentMatchers.eq(""), ArgumentMatchers.anyMap()))
                .thenReturn(token);

        jwtSaml2AuthenticationRequestRepository.saveAuthenticationRequest(
                authRequest, request, response);

        Mockito.verify(request).setAttribute(SAML_REQUEST_TOKEN, relayState);
        Mockito.verify(response).addHeader(SAML_REQUEST_TOKEN, relayState);
    }

    @Test
    void saveAuthenticationRequestWithNullRequest() {
        var request = Mockito.mock(MockHttpServletRequest.class);
        var response = Mockito.mock(MockHttpServletResponse.class);

        jwtSaml2AuthenticationRequestRepository.saveAuthenticationRequest(null, request, response);

        Assertions.assertTrue(tokenStore.isEmpty());
    }

    @Test
    void loadAuthenticationRequest() {
        var request = Mockito.mock(MockHttpServletRequest.class);
        var relyingPartyRegistration = Mockito.mock(RelyingPartyRegistration.class);
        var assertingPartyMetadata = Mockito.mock(AssertingPartyMetadata.class);
        String relayState = "testRelayState";
        String token = "testToken";
        Map<String, Object> claims =
                Map.of(
                        "id", "testId",
                        "relyingPartyRegistrationId", "stirling-pdf",
                        "authenticationRequestUri", "example.com/authnRequest",
                        "samlRequest", "testSamlRequest",
                        "relayState", relayState);

        Mockito.when(request.getParameter("RelayState")).thenReturn(relayState);
        Mockito.when(jwtService.extractClaims(token)).thenReturn(claims);
        Mockito.when(relyingPartyRegistrationRepository.findByRegistrationId("stirling-pdf"))
                .thenReturn(relyingPartyRegistration);
        Mockito.when(relyingPartyRegistration.getRegistrationId()).thenReturn("stirling-pdf");
        Mockito.when(relyingPartyRegistration.getAssertingPartyMetadata())
                .thenReturn(assertingPartyMetadata);
        Mockito.when(assertingPartyMetadata.getSingleSignOnServiceLocation())
                .thenReturn("https://example.com/sso");
        tokenStore.put(relayState, token);

        var result = jwtSaml2AuthenticationRequestRepository.loadAuthenticationRequest(request);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(tokenStore.containsKey(relayState));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void loadAuthenticationRequestWithInvalidRelayState(String relayState) {
        var request = Mockito.mock(MockHttpServletRequest.class);
        Mockito.when(request.getParameter("RelayState")).thenReturn(relayState);

        var result = jwtSaml2AuthenticationRequestRepository.loadAuthenticationRequest(request);

        Assertions.assertNull(result);
    }

    @Test
    void loadAuthenticationRequestWithNonExistentToken() {
        var request = Mockito.mock(MockHttpServletRequest.class);
        Mockito.when(request.getParameter("RelayState")).thenReturn("nonExistentRelayState");

        var result = jwtSaml2AuthenticationRequestRepository.loadAuthenticationRequest(request);

        Assertions.assertNull(result);
    }

    @Test
    void loadAuthenticationRequestWithNullRelyingPartyRegistration() {
        var request = Mockito.mock(MockHttpServletRequest.class);
        String relayState = "testRelayState";
        String token = "testToken";
        Map<String, Object> claims =
                Map.of(
                        "id", "testId",
                        "relyingPartyRegistrationId", "stirling-pdf",
                        "authenticationRequestUri", "example.com/authnRequest",
                        "samlRequest", "testSamlRequest",
                        "relayState", relayState);

        Mockito.when(request.getParameter("RelayState")).thenReturn(relayState);
        Mockito.when(jwtService.extractClaims(token)).thenReturn(claims);
        Mockito.when(relyingPartyRegistrationRepository.findByRegistrationId("stirling-pdf"))
                .thenReturn(null);
        tokenStore.put(relayState, token);

        var result = jwtSaml2AuthenticationRequestRepository.loadAuthenticationRequest(request);

        Assertions.assertNull(result);
    }

    @Test
    void removeAuthenticationRequest() {
        var request = Mockito.mock(HttpServletRequest.class);
        var response = Mockito.mock(HttpServletResponse.class);
        var relyingPartyRegistration = Mockito.mock(RelyingPartyRegistration.class);
        var assertingPartyMetadata = Mockito.mock(AssertingPartyMetadata.class);
        String relayState = "testRelayState";
        String token = "testToken";
        Map<String, Object> claims =
                Map.of(
                        "id", "testId",
                        "relyingPartyRegistrationId", "stirling-pdf",
                        "authenticationRequestUri", "example.com/authnRequest",
                        "samlRequest", "testSamlRequest",
                        "relayState", relayState);

        Mockito.when(request.getParameter("RelayState")).thenReturn(relayState);
        Mockito.when(jwtService.extractClaims(token)).thenReturn(claims);
        Mockito.when(relyingPartyRegistrationRepository.findByRegistrationId("stirling-pdf"))
                .thenReturn(relyingPartyRegistration);
        Mockito.when(relyingPartyRegistration.getRegistrationId()).thenReturn("stirling-pdf");
        Mockito.when(relyingPartyRegistration.getAssertingPartyMetadata())
                .thenReturn(assertingPartyMetadata);
        Mockito.when(assertingPartyMetadata.getSingleSignOnServiceLocation())
                .thenReturn("https://example.com/sso");
        tokenStore.put(relayState, token);

        var result =
                jwtSaml2AuthenticationRequestRepository.removeAuthenticationRequest(
                        request, response);

        Assertions.assertNotNull(result);
        Assertions.assertFalse(tokenStore.containsKey(relayState));
    }

    @Test
    void removeAuthenticationRequestWithNullRelayState() {
        var request = Mockito.mock(HttpServletRequest.class);
        var response = Mockito.mock(HttpServletResponse.class);
        Mockito.when(request.getParameter("RelayState")).thenReturn(null);

        var result =
                jwtSaml2AuthenticationRequestRepository.removeAuthenticationRequest(
                        request, response);

        Assertions.assertNull(result);
    }

    @Test
    void removeAuthenticationRequestWithNonExistentToken() {
        var request = Mockito.mock(HttpServletRequest.class);
        var response = Mockito.mock(HttpServletResponse.class);
        Mockito.when(request.getParameter("RelayState")).thenReturn("nonExistentRelayState");

        var result =
                jwtSaml2AuthenticationRequestRepository.removeAuthenticationRequest(
                        request, response);

        Assertions.assertNull(result);
    }

    @Test
    void removeAuthenticationRequestWithOnlyRelayState() {
        var request = Mockito.mock(HttpServletRequest.class);
        var response = Mockito.mock(HttpServletResponse.class);
        String relayState = "testRelayState";

        Mockito.when(request.getParameter("RelayState")).thenReturn(relayState);

        var result =
                jwtSaml2AuthenticationRequestRepository.removeAuthenticationRequest(
                        request, response);

        Assertions.assertNull(result);
        Assertions.assertFalse(tokenStore.containsKey(relayState));
    }
}
