package org.apache.camel.component.file.remote;


import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileMessage;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.component.file.remote.gateway.SftpClient;
import org.apache.camel.component.file.remote.gateway.SftpSecurityProvider;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SftpOperationsConnectDeepTest {

    SftpOperations sftpOperations;
    @Mock
    private SftpEndpoint endpoint;
    @Mock
    SftpConfiguration configuration;
    @Mock
    SftpClient sftpClient;
    @Mock
    SftpSecurityProvider securityProvider;


    @BeforeEach
    void setUp() {
        sftpOperations = new SftpOperations(sftpClient);
        sftpOperations.setEndpoint(endpoint);
    }


    @Test
    public void connect_widthRetryConfigurationIfException_shouldTryToConnect() {
        when(endpoint.getMaximumReconnectAttempts()).thenReturn(10);
        when(sftpClient.isConnected()).thenReturn(false);
        doThrow(SftpClientException.class)
                .when(sftpClient).init(any());

        assertThrows(GenericFileOperationFailedException.class, () -> sftpOperations.connect(configuration, null));

        verify(sftpClient, times(10)).init(any());
    }

    @Test
    public void connect_widthRetryConfiguration_shouldTryToConnect() {
        when(sftpClient.isConnected()).thenReturn(false);
        when(endpoint.getMaximumReconnectAttempts()).thenReturn(10);
        doThrow(SftpClientException.class)
                .doThrow(SftpClientException.class)
                .doThrow(SftpClientException.class)
                .doNothing()
                .when(sftpClient).init(any());

        sftpOperations.connect(configuration, null);

        verify(sftpClient, times(4)).init(any());
    }
    @Test
    public void retrieveFile(){
        GenericFile<?> file = mock(GenericFile.class);
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(sftpClient.get(anyString())).thenReturn(InputStream.nullInputStream());

        when(exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE))
                .thenReturn(file);

        sftpOperations.retrieveFile("name",exchange,100);
    }

}