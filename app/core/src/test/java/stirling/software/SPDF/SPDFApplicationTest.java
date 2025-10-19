package stirling.software.SPDF;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import stirling.software.common.model.ApplicationProperties;

@ExtendWith(MockitoExtension.class)
public class SPDFApplicationTest {

    @Mock private Environment env;

    @Mock private ApplicationProperties applicationProperties;

    @InjectMocks private SPDFApplication sPDFApplication;

    @BeforeEach
    public void setUp() {
        SPDFApplication.setServerPortStatic("8080");
    }

    @Test
    public void testSetServerPortStatic() {
        SPDFApplication.setServerPortStatic("9090");
        Assertions.assertEquals("9090", SPDFApplication.getStaticPort());
    }

    @Test
    public void testGetStaticPort() {
        Assertions.assertEquals("8080", SPDFApplication.getStaticPort());
    }
}
