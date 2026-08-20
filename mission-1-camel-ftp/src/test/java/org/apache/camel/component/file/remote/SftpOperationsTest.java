package org.apache.camel.component.file.remote;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import org.apache.camel.component.file.remote.gateway.SftpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SftpOperationsTest {
    private static final String FILE_TO_DELETE = "file";
    private final boolean CLIENT_IS_ALREADY_CONNECTED = true;
    private final boolean CLIENT_IS_NOT_CONNECTED = false;

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
        when(sftpClient.isConnected()).thenReturn(CLIENT_IS_ALREADY_CONNECTED);
        assertTrue(sftp.connect(null, null));
    }

    @Test
    public void connect_WhenClientIsNotConnected_ShouldReturnOk() {
        sftp.setEndpoint(endpoint);
        when(sftpClient.isConnected()).thenReturn(CLIENT_IS_NOT_CONNECTED);
        assertTrue(sftp.connect(configuration, null));
        verify(sftpClient).init(any());
    }

    @Test
    public void isConnected_WhenClientIsNotConnected_ShouldReturnFalse() {
        assertFalse(sftp.isConnected());
    }

    @Test
    public void isConnected_WhenClientIsAlreadyConnected_ShouldReturnFalse() {
        when(sftpClient.isConnected()).thenReturn(CLIENT_IS_ALREADY_CONNECTED);
        assertTrue(sftp.isConnected());
    }

    @Test
    public void disconnect_MustCallClientMethod() {
        sftp.disconnect();
        verify(sftpClient).disconnectSftp();
    }

    @Test
    public void forceDisconnectTest_MustCallClientMethod() {
        sftp.forceDisconnect();
        verify(sftpClient).forceDisconnect();
    }

    @Test
    public void deleteFile_WhenClientIsAlreadyConnected_ShouldReturnOk() {

        sftp.setEndpoint(endpoint);
        when(sftpClient.isConnected()).thenReturn(CLIENT_IS_ALREADY_CONNECTED);
        assertTrue(sftp.deleteFile(FILE_TO_DELETE));
        verify(sftpClient).rm(FILE_TO_DELETE);
    }

    @Test
    public void deleteFile_WhenClientIsNotConnected_ShouldReturnOk() {

        sftp.setEndpoint(endpoint);

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(sftpClient.isConnected()).thenReturn(CLIENT_IS_NOT_CONNECTED);

        assertTrue(sftp.deleteFile(FILE_TO_DELETE));
        verify(sftpClient).rm(FILE_TO_DELETE);
    }

    @Test
    public void renameFile_WhenClientIsNotConnected_ShouldReturnOk() {
        sftp.setEndpoint(endpoint);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(sftpClient.isConnected()).thenReturn(CLIENT_IS_NOT_CONNECTED);
        sftp.renameFile("FILENAME_FROM", "FILENAME_TO");
        verify(sftpClient).channelRename("FILENAME_FROM", "FILENAME_TO");
    }

    @Test
    public void renameFile_WhenClientIsAlreadyConnected_ShouldReturnOk() {
        when(sftpClient.isConnected()).thenReturn(CLIENT_IS_ALREADY_CONNECTED);
        sftp.renameFile("FILENAME_FROM", "FILENAME_TO");
        verify(sftpClient).channelRename("FILENAME_FROM", "FILENAME_TO");
    }

    @Test
    //todo пока оставим там, далее нужно проверить логику
    public void buildDirectory() {
        sftp.setEndpoint(endpoint);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        sftp.buildDirectory("DIRECTORY", false);
    }

    @Test
    public void getCurrentDirectory_shouldDelegateToSftpClientPwd() {
        String currentDirectory = "CURRENT_DIRECTORY";
        when(sftpClient.pwd()).thenReturn(currentDirectory);
        assertEquals(sftp.getCurrentDirectory(), currentDirectory);
    }

    @Test
    //todo пока оставим там, далее нужно проверить логику
    public void changeCurrentDirectory() {
        String currentDirectory = "CURRENT_DIRECTORY";
        when(endpoint.getConfiguration()).thenReturn(configuration);
        sftp.setEndpoint(endpoint);

        sftp.changeCurrentDirectory(currentDirectory);
    }

    @Test
    //todo пока оставим там, далее нужно проверить логику
    public void changeToParentDirectory(){
        when(endpoint.getConfiguration()).thenReturn(configuration);
        sftp.setEndpoint(endpoint);

        sftp.changeToParentDirectory();
    }

    @Test
    //todo нельзя проверить потому что не могу создать LsEntry из за конструктора.
    public void listFiles(){
        String path = "MY_PATH";
        when(sftpClient.ls(path)).thenReturn(new Vector<>());
        sftp.listFiles(path);
    }

    @Test
    //todo нельзя проверить потому что не могу создать LsEntry из за конструктора.
    public void listFiles_WithParameters(){
        String path = "MY_PATH";
        when(sftpClient.ls(path)).thenReturn(new Vector<>());
        sftp.listFiles(path);
    }

    @Test
    @Disabled
    //todo здесь пока нельзя проверить без camel.exchange
    public void retrieveFile(){
        sftp.setEndpoint(endpoint);
        sftp.retrieveFile(null,null,0);
    }

    @Test
    @Disabled
    //todo здесь пока нельзя проверить без camel.exchange
    public void releaseRetrievedFileResources(){
        sftp.releaseRetrievedFileResources(null);
    }

    @Test
    @Disabled
    //todo здесь пока нельзя проверить без camel.exchange
    public void storeFile(){
        sftp.storeFile(null,null,0);
    }

    @Test
    public void storeFileDirectly_shouldDelegateToSftpClientPut(){
        String payload = "PAYLOAD";
        String name = "NAME";
        sftp.storeFileDirectly(name,payload);
        verify(sftpClient).put(eq(name),any(ByteArrayInputStream.class));
    }

    @Test
    //todo пока оставим там, далее нужно проверить логику
    public void existsFile_shouldDelegateToSftpClientPut(){
        sftp.setEndpoint(endpoint);
        sftp.existsFile("NAME");
    }

    @Test
    public void sendNoop_WhenClientIsAlreadyConnected_ShouldDelegateToSftpClientSendAliveMsg(){
        when(sftpClient.isConnected()).thenReturn(CLIENT_IS_ALREADY_CONNECTED);
        when(sftpClient.sendKeepAliveMsg()).thenReturn(true);

        assertTrue(sftp.sendNoop());
        verify(sftpClient).sendKeepAliveMsg();
    }
    @Test
    public void sendSiteCommand_ShouldReturnTrue(){
        assertTrue(sftp.sendSiteCommand(null));
    }
}