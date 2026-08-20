package org.apache.camel.component.file.remote;


import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.component.file.remote.gateway.SftpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

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
        sftpOperations = new TestSftpOperations(sftpClient);
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
    @Test
    public void connect_widthCertificateFileNotNull() {
        when(configuration.getCertFile()).thenReturn("My private key path");
        sftpOperations.connect(configuration, null);
    }


    //фиксируем запахи
    class TestSftpOperations extends SftpOperations{
        public TestSftpOperations(SftpClient client){
            super(sftpClient);
        }
        @Override
        protected byte[] loadPrivateKey(SftpConfiguration sftpConfig) {
            return new byte[0];
        }

        @Override
        protected byte[] loadCertFromFile(String filePath) {
            return new byte[0];
        }

        @Override
        protected byte[] loadCertFromUri(String certUri) {
            return new byte[0];
        }
    }

}