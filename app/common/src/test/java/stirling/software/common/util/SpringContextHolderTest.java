package stirling.software.common.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

class SpringContextHolderTest {

    private ApplicationContext mockApplicationContext;
    private SpringContextHolder contextHolder;

    @BeforeEach
    void setUp() {
        mockApplicationContext = Mockito.mock(ApplicationContext.class);
        contextHolder = new SpringContextHolder();
    }

    @Test
    void testSetApplicationContext() {
        // Act
        contextHolder.setApplicationContext(mockApplicationContext);

        // Assert
        Assertions.assertTrue(SpringContextHolder.isInitialized());
    }

    @Test
    void testGetBean_ByType() {
        // Arrange
        contextHolder.setApplicationContext(mockApplicationContext);
        TestBean expectedBean = new TestBean();
        Mockito.when(mockApplicationContext.getBean(TestBean.class)).thenReturn(expectedBean);

        // Act
        TestBean result = SpringContextHolder.getBean(TestBean.class);

        // Assert
        Assertions.assertSame(expectedBean, result);
        Mockito.verify(mockApplicationContext).getBean(TestBean.class);
    }

    @Test
    void testGetBean_ApplicationContextNotSet() {
        // Don't set application context

        // Act
        TestBean result = SpringContextHolder.getBean(TestBean.class);

        // Assert
        Assertions.assertNull(result);
    }

    @Test
    void testGetBean_BeanNotFound() {
        // Arrange
        contextHolder.setApplicationContext(mockApplicationContext);
        Mockito.when(mockApplicationContext.getBean(TestBean.class))
                .thenThrow(new MyBeansException());

        // Act
        TestBean result = SpringContextHolder.getBean(TestBean.class);

        // Assert
        Assertions.assertNull(result);
    }

    // Simple test class
    private static class TestBean {}

    private static class MyBeansException extends org.springframework.beans.BeansException {
        public MyBeansException() {
            super("Bean not found");
        }
    }
}
