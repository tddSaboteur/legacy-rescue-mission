package org.apache.camel.component.file.remote;


import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.component.file.remote.gateway.SftpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @BeforeEach
    void setUp() {
        sftpOperations = new SftpOperations(sftpClient);
        sftpOperations.setEndpoint(endpoint);
    }


    @Test
    public void connect_widthRetryConfigurationIfException_shouldTryToConnect() {
        when(endpoint.getMaximumReconnectAttempts()).thenReturn(10);
        doThrow(SftpClientException.class)
                .when(sftpClient).init(any());

        assertThrows(GenericFileOperationFailedException.class, () -> sftpOperations.connect(configuration, null));

        verify(sftpClient, times(10)).init(any());
    }

    @Test
    public void connect_widthRetryConfiguration_shouldTryToConnect() {
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
    public void connect_widthPrivateKeyNotNull() {
        when(configuration.getPrivateKeyUri()).thenReturn("My private key path");
        sftpOperations.connect(configuration, null);
    }

}