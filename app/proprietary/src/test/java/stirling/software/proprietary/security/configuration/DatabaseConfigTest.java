package stirling.software.proprietary.security.configuration;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.exception.UnsupportedProviderException;

@ExtendWith(MockitoExtension.class)
class DatabaseConfigTest {

    @Mock private ApplicationProperties.Datasource datasource;

    private DatabaseConfig databaseConfig;

    @BeforeEach
    void setUp() {
        databaseConfig = new DatabaseConfig(datasource, true);
    }

    @Test
    void testDataSource_whenRunningEEIsFalse() throws UnsupportedProviderException {
        databaseConfig = new DatabaseConfig(datasource, false);

        var result = databaseConfig.dataSource();

        Assertions.assertInstanceOf(DataSource.class, result);
    }

    @Test
    void testDefaultConfigurationForDataSource() throws UnsupportedProviderException {
        Mockito.when(datasource.isEnableCustomDatabase()).thenReturn(false);

        var result = databaseConfig.dataSource();

        Assertions.assertInstanceOf(DataSource.class, result);
    }

    @Test
    void testCustomUrlForDataSource() throws UnsupportedProviderException {
        Mockito.when(datasource.isEnableCustomDatabase()).thenReturn(true);
        Mockito.when(datasource.getCustomDatabaseUrl()).thenReturn("jdbc:postgresql://mockUrl");
        Mockito.when(datasource.getUsername()).thenReturn("test");
        Mockito.when(datasource.getPassword()).thenReturn("pass");

        var result = databaseConfig.dataSource();

        Assertions.assertInstanceOf(DataSource.class, result);
    }

    @Test
    void testCustomConfigurationForDataSource() throws UnsupportedProviderException {
        Mockito.when(datasource.isEnableCustomDatabase()).thenReturn(true);
        Mockito.when(datasource.getCustomDatabaseUrl()).thenReturn("");
        Mockito.when(datasource.getType()).thenReturn("postgresql");
        Mockito.when(datasource.getHostName()).thenReturn("test");
        Mockito.when(datasource.getPort()).thenReturn(1234);
        Mockito.when(datasource.getName()).thenReturn("test_db");
        Mockito.when(datasource.getUsername()).thenReturn("test");
        Mockito.when(datasource.getPassword()).thenReturn("pass");

        var result = databaseConfig.dataSource();

        Assertions.assertInstanceOf(DataSource.class, result);
    }

    @ParameterizedTest(name = "Exception thrown when the DB type [{arguments}] is not supported")
    @ValueSource(strings = {"oracle", "mysql", "mongoDb"})
    void exceptionThrown_whenDBTypeIsUnsupported(String datasourceType) {
        Mockito.when(datasource.isEnableCustomDatabase()).thenReturn(true);
        Mockito.when(datasource.getCustomDatabaseUrl()).thenReturn("");
        Mockito.when(datasource.getType()).thenReturn(datasourceType);

        Assertions.assertThrows(
                UnsupportedProviderException.class, () -> databaseConfig.dataSource());
    }
}
