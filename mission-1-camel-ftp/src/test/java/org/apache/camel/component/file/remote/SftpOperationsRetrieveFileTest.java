package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.remote.gateway.SftpClient;
import org.apache.camel.component.file.remote.gateway.SftpFileMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SftpOperationsRetrieveFileTest {
    static final String RELATIVE_PATH = "./relative/path";
    static final String FILE_NAME = "name";
    static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    SftpOperations sftpOperations;
    @Mock
    private SftpEndpoint endpoint;
    @Mock
    SftpConfiguration configuration;
    @Mock
    SftpClient sftpClient;
    @Mock
    Exchange exchange;
    @Mock
    Message message;

    @BeforeEach
    void setUp() {
        sftpOperations = new SftpOperations(sftpClient);
        sftpOperations.setEndpoint(endpoint);
        when(exchange.getIn()).thenReturn(message);
        when(configuration.isStepwise()).thenReturn(true);
        when(endpoint.getConfiguration()).thenReturn(configuration);
    }

    @Test
    public void retrieveFile_whenStoreFileContentDirectoryAndStreamOnTheBodyFalse(){

        when(endpoint.getLocalWorkDirectory()).thenReturn(null);

        when(exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE)).thenReturn(new GenericFile<>());
        when(configuration.isStreamDownload()).thenReturn(false);

        doNothing().when(sftpClient).get(eq(FILE_NAME),any());
        assertTrue(sftpOperations.retrieveFile(FILE_NAME,exchange,100));

        verify(sftpClient,times(1)).get(eq(FILE_NAME), any(OutputStream.class));

    }

    @Test
    public void retrieveFile_whenLocalWorkDirectoryIsConfigured_shouldStoreFile(){

        when(endpoint.getLocalWorkDirectory()).thenReturn(TEMP_DIR);
        var genericFile = new GenericFile<>();
        genericFile.setRelativeFilePath(RELATIVE_PATH);

        when(exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE)).thenReturn(genericFile);

        assertTrue(sftpOperations.retrieveFile(FILE_NAME,exchange,100));
        verify(sftpClient,times(1)).get(eq(FILE_NAME), any(OutputStream.class));
    }
    @Test
    public void retrieveFile_whenStoreFileContentDirectoryAsStreamOnTheBody() throws Exception {
        String path = "MY_PATH";


        when(endpoint.getLocalWorkDirectory()).thenReturn(null);


        when(configuration.isStepwise()).thenReturn(true);
        when(configuration.isStreamDownload()).thenReturn(true);

        var genericFile = new GenericFile<SftpFileMetadata>();
        when(exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE)).thenReturn(genericFile);

        assertTrue(sftpOperations.retrieveFile(path, exchange, 100L));
        verify(sftpClient, times(1)).get(eq(path), any(OutputStream.class));

        assertNotNull(genericFile.getBody());
        assertTrue(genericFile.getBody() instanceof java.io.InputStream);

        verify(message, times(1)).setHeader(
                eq(FtpConstants.REMOTE_FILE_INPUT_STREAM),
                any(java.io.InputStream.class)
        );
    }
}
