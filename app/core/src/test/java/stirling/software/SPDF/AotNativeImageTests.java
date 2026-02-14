package stirling.software.SPDF;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.EnabledIf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@EnabledIf(value = "#{systemProperties['spring.aot.enabled'] == 'true'}", loadContext = true)
class AotNativeImageTests {

    @Autowired private ApplicationContext context;

    @Autowired(required = false)
    private stirling.software.common.configuration.AppConfig appConfig;

    @Autowired(required = false)
    private stirling.software.common.model.ApplicationProperties.Datasource datasource;

    @Test
    void contextLoadsInAotMode() {
        System.out.println("DEBUG: MAIL env var: '" + System.getenv("MAIL") + "'");
        System.out.println("DEBUG: AppConfig: " + appConfig);
        System.out.println("DEBUG: Datasource: " + datasource);
        assertThat(context).isNotNull();
    }

    @Test
    void pdfBoxOperationsWork() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void jacksonSerializationWorks() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> data = Map.of("key", "value");

        String json = mapper.writeValueAsString(data);
        assertThat(json).contains("key");
    }
}
