package stirling.software.SPDF.model.api.converters;

import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class PdfVectorExportRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void whenOutputFormatValid_thenNoViolations() {
        PdfVectorExportRequest request = new PdfVectorExportRequest();
        request.setOutputFormat("EPS");

        Set<ConstraintViolation<PdfVectorExportRequest>> violations = validator.validate(request);

        Assertions.assertThat(violations).isEmpty();
    }

    @Test
    void whenOutputFormatInvalid_thenConstraintViolation() {
        PdfVectorExportRequest request = new PdfVectorExportRequest();
        request.setOutputFormat("svg");

        Set<ConstraintViolation<PdfVectorExportRequest>> violations = validator.validate(request);

        Assertions.assertThat(violations).hasSize(1);
        Assertions.assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("outputFormat");
    }
}
