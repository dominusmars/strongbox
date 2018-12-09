package org.carlspring.strongbox.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;

import javax.inject.Inject;

import org.carlspring.strongbox.booters.PropertiesBooter;
import org.carlspring.strongbox.config.IntegrationTest;
import org.carlspring.strongbox.rest.common.RestAssuredBaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * @author Pablo Tirado
 * @author Aditya Srinivasan
 */
// TODO migrate to https://docs.spring.io/spring-boot/docs/current/actuator-api/html/#loggers after https://github.com/strongbox/strongbox/issues/1046
@IntegrationTest
public class LoggingManagementControllerTestIT
        extends RestAssuredBaseTest
{

    private static final String LOGGER_LEVEL = Level.INFO.getName();

    private static final String LOGGER_APPENDER = "CONSOLE";

    @Inject
    private PropertiesBooter propertiesBooter;

    @Override
    @BeforeEach
    public void init()
            throws Exception
    {
        super.init();
        setContextBaseUrl("/api/logging");
    }

    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    @WithMockUser(authorities = { "CONFIGURATION_ADD_LOGGER" })
    void testAddLogger(String acceptHeader)
    {
        addLogger(acceptHeader, "org.carlspring.strongbox.logging.test", LOGGER_LEVEL, LOGGER_APPENDER);
    }

    private void addLogger(String acceptHeader,
                           String loggerPackage,
                           String level,
                           String appender)
    {
        String url = getContextBaseUrl() + "/logger";

        mockMvc.accept(acceptHeader)
               .param("logger", loggerPackage)
               .param("level", level)
               .param("appenderName", appender)
               .when()
               .put(url)
               .peek() // Use peek() to print the output
               .then()
               .statusCode(HttpStatus.OK.value()) // check http status code
               .body(containsString("The logger was added successfully."));
    }

    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    @WithMockUser(authorities = { "CONFIGURATION_ADD_LOGGER",
                                  "CONFIGURATION_UPDATE_LOGGER" })
    void testUpdateLogger(String acceptHeader)
    {
        String loggerPackage = "org.carlspring.strongbox.test.log.update";

        addLogger(acceptHeader, loggerPackage, LOGGER_LEVEL, LOGGER_APPENDER);

        String url = getContextBaseUrl() + "/logger";

        mockMvc.accept(acceptHeader)
               .param("logger", loggerPackage)
               .param("level", LOGGER_LEVEL)
               .when()
               .post(url)
               .peek() // Use peek() to print the output
               .then()
               .statusCode(HttpStatus.OK.value()) // check http status code
               .body(containsString("The logger was updated successfully."));
    }

    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    @WithMockUser(authorities = { "CONFIGURATION_UPDATE_LOGGER" })
    void testUpdateLoggerNotFound(String acceptHeader)
    {
        String url = getContextBaseUrl() + "/logger";
        String loggerPackage = "org.carlspring.strongbox.test.log.non.existing";

        mockMvc.accept(acceptHeader)
               .param("logger", loggerPackage)
               .param("level", LOGGER_LEVEL)
               .when()
               .post(url)
               .peek() // Use peek() to print the output
               .then()
               .statusCode(HttpStatus.NOT_FOUND.value()) // check http status code
               .body(containsString("Logger '" + loggerPackage + "' not found!"));
    }

    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    @WithMockUser(authorities = { "CONFIGURATION_ADD_LOGGER",
                                  "CONFIGURATION_DELETE_LOGGER" })
    void testDeleteLogger(String acceptHeader)
    {
        String packageName = "org.carlspring.strongbox.test.log.delete";

        addLogger(acceptHeader, packageName, LOGGER_LEVEL, LOGGER_APPENDER);

        String url = getContextBaseUrl() + "/logger";

        mockMvc.accept(acceptHeader)
               .param("logger", packageName)
               .when()
               .delete(url)
               .peek() // Use peek() to print the output
               .then()
               .statusCode(HttpStatus.OK.value()) // check http status code
               .body(containsString("The logger was deleted successfully."));
    }

    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    @WithMockUser(authorities = { "CONFIGURATION_DELETE_LOGGER" })
    void testDeleteLoggerNotFound(String acceptHeader)
    {
        String url = getContextBaseUrl() + "/logger";

        String loggerPackage = "org.carlspring.strongbox.test.log.delete.non.existing";


        mockMvc.accept(acceptHeader)
               .param("logger", loggerPackage)
               .when()
               .delete(url)
               .peek() // Use peek() to print the output
               .then()
               .statusCode(HttpStatus.NOT_FOUND.value()) // check http status code
               .body(containsString("Logger '" + loggerPackage + "' not found!"));
    }

    @Test
    @WithMockUser(authorities = { "CONFIGURE_LOGS" })
    void testDownloadLog()
            throws Exception
    {
        String testLogName = "strongbox-test.log";
        Files.createFile(Paths.get(propertiesBooter.getLogsDirectory(), testLogName))
             .toFile()
             .deleteOnExit();

        String url = getContextBaseUrl() + "/log/{path}";

        mockMvc.accept(MediaType.TEXT_PLAIN_VALUE)
               .when()
               .get(url, testLogName)
               .peek() // Use peek() to print the output
               .then()
               .statusCode(HttpStatus.OK.value()); // check http status code
    }

    @Test
    @WithMockUser(authorities = { "CONFIGURATION_RETRIEVE_LOGBACK_CFG" })
    void testDownloadLogbackConfiguration()
    {
        String url = getContextBaseUrl() + "/logback";

        mockMvc.accept(MediaType.APPLICATION_XML_VALUE)
               .when()
               .get(url)
               .peek() // Use peek() to print the output
               .then()
               .statusCode(HttpStatus.OK.value()); // check http status code
    }

    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    @WithMockUser(authorities = { "CONFIGURATION_UPLOAD_LOGBACK_CFG",
                                  "CONFIGURATION_RETRIEVE_LOGBACK_CFG" })
    void testUploadLogbackConfiguration(String acceptHeader)
    {
        String url = getContextBaseUrl() + "/logback";

        // Obtain the current logback XML.
        byte[] byteArray = mockMvc.accept(MediaType.APPLICATION_XML_VALUE)
                                  .get(url)
                                  .peek()
                                  .then()
                                  .statusCode(HttpStatus.OK.value())
                                  .extract()
                                  .asByteArray();


        mockMvc.accept(acceptHeader)
               .contentType(MediaType.APPLICATION_XML_VALUE)
               .body(byteArray)
               .when()
               .post(url)
               .peek() // Use peek() to print the output
               .then()
               .statusCode(HttpStatus.OK.value()) // check http status code
               .body(containsString("Logback configuration uploaded successfully."));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_LOGS" })
    void testLogDirectoryForListOfLogFiles()
    {
        //Given
        //Create dummy test files here
        Path[] paths = createTestLogFilesAndDirectories();

        String[] tempLogFilesArray = new String[4];
        for (int i = 0; i < paths.length; i++)
        {
            tempLogFilesArray[i] = paths[i].getFileName().toString();
        }

        String logDirectoryHomeUrl = getContextBaseUrl() + "/logs/";

        //When
        //Getting the table elements
        String tableElementsAsString = mockMvc.contentType(MediaType.TEXT_PLAIN_VALUE)
                                              .when()
                                              .get(logDirectoryHomeUrl)
                                              .body()
                                              .htmlPath()
                                              .getString("html.body.table");

        //Assertion Test to see if given file names are contained in the HTML body
        boolean shouldContainLogFilesInHtmlTableElement = false;
        if (tableElementsAsString.contains(tempLogFilesArray[0])
            && tableElementsAsString.contains(tempLogFilesArray[1])
            && tableElementsAsString.contains(tempLogFilesArray[2])
            && tableElementsAsString.contains(tempLogFilesArray[3]))
        {
            shouldContainLogFilesInHtmlTableElement = true;
        }
        
        assertThat(shouldContainLogFilesInHtmlTableElement).isEqualTo("The log files should be in the HTML response body!");
    }

    //This method creates temporary log files, and if necessary for subdirectory browsing, a log subdirectory.
    private Path[] createTestLogFilesAndDirectories()
    {
        //If a test directory is needed, a new directory called `test` under `/logs/` will be created.
        //Otherwise the path of `/logs` will be returned.
        Path logDirectoryPath;
        Path[] paths = new Path[4];
        try
        {
            logDirectoryPath = Paths.get(propertiesBooter.getLogsDirectory());

            //Create 4 temporary log files from 0 to 3.
            for (int i = 0; i < 4; i++)
            {
                paths[i] = Files.createTempFile(logDirectoryPath, "TestLogFile" + i, ".log");
            }
        }
        catch (IOException e)
        {
            fail("Unable to create test log files and/or directories!");
        }

        return paths;
    }

}
