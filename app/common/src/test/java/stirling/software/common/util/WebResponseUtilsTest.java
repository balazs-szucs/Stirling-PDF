package stirling.software.common.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

public class WebResponseUtilsTest {

    @Test
    public void testBoasToWebResponse() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write("Sample PDF content".getBytes());
            String docName = "sample.pdf";

            ResponseEntity<byte[]> responseEntity =
                    WebResponseUtils.baosToWebResponse(baos, docName);

            Assertions.assertNotNull(responseEntity);
            Assertions.assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
            Assertions.assertNotNull(responseEntity.getBody());

            HttpHeaders headers = responseEntity.getHeaders();
            Assertions.assertNotNull(headers);
            Assertions.assertEquals(MediaType.APPLICATION_PDF, headers.getContentType());
            Assertions.assertNotNull(headers.getContentDisposition());
            // assertEquals("attachment; filename=\"sample.pdf\"",
            // headers.getContentDisposition().toString());

        } catch (IOException e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testMultiPartFileToWebResponse() {
        try {
            byte[] fileContent = "Sample file content".getBytes();
            MockMultipartFile file =
                    new MockMultipartFile(
                            "file", "sample.txt", MediaType.TEXT_PLAIN_VALUE, fileContent);

            ResponseEntity<byte[]> responseEntity =
                    WebResponseUtils.multiPartFileToWebResponse(file);

            Assertions.assertNotNull(responseEntity);
            Assertions.assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
            Assertions.assertNotNull(responseEntity.getBody());

            HttpHeaders headers = responseEntity.getHeaders();
            Assertions.assertNotNull(headers);
            Assertions.assertEquals(MediaType.TEXT_PLAIN, headers.getContentType());
            Assertions.assertNotNull(headers.getContentDisposition());

        } catch (IOException e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testBytesToWebResponse() {
        try {
            byte[] bytes = "Sample bytes".getBytes();
            String docName = "sample.txt";
            MediaType mediaType = MediaType.TEXT_PLAIN;

            ResponseEntity<byte[]> responseEntity =
                    WebResponseUtils.bytesToWebResponse(bytes, docName, mediaType);

            Assertions.assertNotNull(responseEntity);
            Assertions.assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
            Assertions.assertNotNull(responseEntity.getBody());

            HttpHeaders headers = responseEntity.getHeaders();
            Assertions.assertNotNull(headers);
            Assertions.assertEquals(MediaType.TEXT_PLAIN, headers.getContentType());
            Assertions.assertNotNull(headers.getContentDisposition());

        } catch (IOException e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }

    @Test
    public void testPdfDocToWebResponse() {
        try {
            PDDocument document = new PDDocument();
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            String docName = "sample.pdf";

            ResponseEntity<byte[]> responseEntity =
                    WebResponseUtils.pdfDocToWebResponse(document, docName);

            Assertions.assertNotNull(responseEntity);
            Assertions.assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
            Assertions.assertNotNull(responseEntity.getBody());

            HttpHeaders headers = responseEntity.getHeaders();
            Assertions.assertNotNull(headers);
            Assertions.assertEquals(MediaType.APPLICATION_PDF, headers.getContentType());
            Assertions.assertNotNull(headers.getContentDisposition());

        } catch (IOException e) {
            Assertions.fail("Exception thrown: " + e.getMessage());
        }
    }
}
