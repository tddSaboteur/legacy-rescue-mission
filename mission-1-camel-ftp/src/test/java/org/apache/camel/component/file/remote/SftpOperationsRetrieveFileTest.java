package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.remote.gateway.SftpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.io.InputStream;
import java.io.OutputStream;

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
    public void retrieveFile_whenStoreFileContentDirectoryAsStreamOnTheBody(){

        when(endpoint.getLocalWorkDirectory()).thenReturn(null);

        when(exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE)).thenReturn(new GenericFile<>());

        when(sftpClient.get(FILE_NAME)).thenReturn(InputStream.nullInputStream());

        assertTrue(sftpOperations.retrieveFile(FILE_NAME,exchange,100));
        verify(sftpClient).get(eq(FILE_NAME));
    }

    @Test
    public void retrieveFile_whenLocalWorkDirectoryIsConfigured_shouldStoreFile(){

        when(endpoint.getLocalWorkDirectory()).thenReturn(TEMP_DIR);
        var genericFile = new GenericFile<>();
        genericFile.setRelativeFilePath(RELATIVE_PATH);

        when(exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE)).thenReturn(genericFile);

        assertTrue(sftpOperations.retrieveFile(FILE_NAME,exchange,100));
        verify(sftpClient).get(eq(FILE_NAME), any(OutputStream.class));
    }
}
