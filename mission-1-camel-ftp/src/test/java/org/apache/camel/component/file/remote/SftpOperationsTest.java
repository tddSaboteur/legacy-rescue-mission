package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.component.file.remote.gateway.SftpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SftpOperationsTest {

    private SftpOperations sftp;
    @Mock
    SftpClient sftpClient;

    @BeforeEach
    public void setUp() {
        sftp = new SftpOperations(sftpClient);
        sftp.setEndpoint(null);
    }

    @Test
    public void connect() {
        when(sftpClient.isConnected()).thenReturn(true);
        sftp.connect(null, null);
    }
}