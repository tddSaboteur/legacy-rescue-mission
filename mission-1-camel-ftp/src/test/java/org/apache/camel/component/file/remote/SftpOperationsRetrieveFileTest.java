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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SftpOperationsRetrieveFileTest {
    SftpOperations sftpOperations;
    @Mock
    private SftpEndpoint endpoint;
    @Mock
    SftpConfiguration configuration;
    @Mock
    SftpClient sftpClient;

    @BeforeEach
    void setUp() {
        sftpOperations = new SftpOperations(sftpClient);
        sftpOperations.setEndpoint(endpoint);
    }

    @Test
    public void retrieveFile_whenStoreFileContentDirectoryAsStreamOnTheBody(){

        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(endpoint.getLocalWorkDirectory()).thenReturn(null);

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(configuration.isStepwise()).thenReturn(true);

        when(exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE)).thenReturn(new GenericFile<>());

        when(sftpClient.get("name")).thenReturn(InputStream.nullInputStream());

        assertTrue(sftpOperations.retrieveFile("name",exchange,100));
        verify(sftpClient).get(anyString());
    }

    @Test
    public void retrieveFile_whenLocalWorkDirectoryIsConfigured_shouldStoreFile(){
        String tempDir = System.getProperty("java.io.tmpdir");
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(endpoint.getLocalWorkDirectory()).thenReturn(tempDir);
        var genericFile = new GenericFile<>();
        genericFile.setRelativeFilePath("./relative/path");

        when(exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE)).thenReturn(genericFile);

        when(configuration.isStepwise()).thenReturn(true);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        assertTrue(sftpOperations.retrieveFile("name",exchange,100));
        verify(sftpClient).get(anyString(), any(OutputStream.class));
    }
}
