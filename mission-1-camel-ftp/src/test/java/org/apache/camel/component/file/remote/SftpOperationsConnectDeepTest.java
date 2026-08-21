package org.apache.camel.component.file.remote;


import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.component.file.remote.gateway.SftpClient;
import org.apache.camel.component.file.remote.gateway.SftpSecurityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
    @Mock
    SftpSecurityProvider securityProvider;


    @BeforeEach
    void setUp() {
        sftpOperations = new TestSftpOperations();
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
        when(securityProvider.loadPrivateKey(any())).thenReturn(new byte[0]);
        sftpOperations.connect(configuration, null);
    }
    @Test
    public void connect_widthCertificateFileNotNull() {
        when(configuration.getCertFile()).thenReturn("My private key path");
        when(securityProvider.resolveCertificateBytes(any())).thenReturn(new byte[0]);
        sftpOperations.connect(configuration, null);
    }

    @Test
    public void connect_widthCertificateUriNotNull() {
        when(configuration.getCertUri()).thenReturn("My private key path");
        when(securityProvider.resolveCertificateBytes(any())).thenReturn(new byte[0]);
        sftpOperations.connect(configuration, null);
    }

    @Test
    public void connect_widthKnownHostsNotNull() {
        when(configuration.getKnownHostsUri()).thenReturn("uri");
        when(securityProvider.loadKnownHostsIS(anyString())).thenReturn(InputStream.nullInputStream());
        sftpOperations.connect(configuration, null);
    }


    //фиксируем запахи
    class TestSftpOperations extends SftpOperations{
        public TestSftpOperations(){
            super(sftpClient,securityProvider);
        }

    }

}