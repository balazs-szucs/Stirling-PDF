package stirling.software.common.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;

public class ErrorUtilsTest {

    @Test
    public void testExceptionToModel() {
        // Create a mock Model
        Model model = new org.springframework.ui.ExtendedModelMap();

        // Create a test exception
        Exception ex = new Exception("Test Exception");

        // Call the method under test
        Model resultModel = ErrorUtils.exceptionToModel(model, ex);

        // Verify the result
        Assertions.assertNotNull(resultModel);
        Assertions.assertEquals("Test Exception", resultModel.getAttribute("errorMessage"));
        Assertions.assertNotNull(resultModel.getAttribute("stackTrace"));
    }

    @Test
    public void testExceptionToModelView() {
        // Create a mock Model
        Model model = new org.springframework.ui.ExtendedModelMap();

        // Create a test exception
        Exception ex = new Exception("Test Exception");

        // Call the method under test
        ModelAndView modelAndView = ErrorUtils.exceptionToModelView(model, ex);

        // Verify the result
        Assertions.assertNotNull(modelAndView);
        Assertions.assertEquals("Test Exception", modelAndView.getModel().get("errorMessage"));
        Assertions.assertNotNull(modelAndView.getModel().get("stackTrace"));
    }
}
