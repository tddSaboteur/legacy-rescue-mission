package org.apache.camel.component.file.remote;

import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.remote.gateway.SftpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SftpOperationsTest {

    private SftpOperations sftp;
    @Mock
    SftpClient sftpClient;
    @Mock
    private SftpEndpoint endpoint;
    @Mock
    SftpConfiguration configuration;


    @BeforeEach
    public void setUp() {
        sftp = new SftpOperations(sftpClient);
    }

    @Test
    public void connect_WhenClientIsAlreadyConnected_ShouldReturnOk() {
        when(sftpClient.isConnected()).thenReturn(true);
        assertTrue(sftp.connect(null, null));
    }
    @Test
    public void connect_WhenClientIsNotConnected_ShouldReturnOk() {
        sftp.setEndpoint(endpoint);
        when(sftpClient.isConnected()).thenReturn(false);
        assertTrue(sftp.connect(configuration, null));
        verify(sftpClient).init(any());
    }
    @Test
    public void disconnect_WhenClientIsAlreadyConnected_ShouldReturnOk() {
        sftp.disconnect();
        verify(sftpClient).disconnectSftp();
    }

    @Test
    public void forceDisconnect_WhenClientIsAlreadyConnected_ShouldReturnOk(){
        sftp.forceDisconnect();
        verify(sftpClient).forceDisconnect();
    }
}