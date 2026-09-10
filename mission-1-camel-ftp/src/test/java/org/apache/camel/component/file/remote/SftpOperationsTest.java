package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileBinding;
import org.apache.camel.component.file.remote.gateway.SftpClient;
import org.apache.camel.component.file.remote.gateway.SftpFileMetadata;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SftpOperationsTest {
    public static final String MY_PATH = "MY_PATH";
    public static final String TEST_FILENAME = "FILENAME";

    public static final SftpFileMetadata FULL_SFTP_FILE_METADATA = new SftpFileMetadata(TEST_FILENAME, "LONG_NAME", 11L, 1, false);
    public static final String THIS_PATH = ".";
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
        verify(sftpClient, never()).init(any());
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
        assertTrue(sftp.deleteFile(TEST_FILENAME));
        verify(sftpClient).rm(TEST_FILENAME);
    }

    @Test
    public void deleteFile_WhenClientIsNotConnected_ShouldReturnOk() {

        sftp.setEndpoint(endpoint);

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(sftpClient.isConnected()).thenReturn(CLIENT_IS_NOT_CONNECTED);

        assertTrue(sftp.deleteFile(TEST_FILENAME));
        verify(sftpClient).rm(TEST_FILENAME);
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
    public void listFiles_mustReturnVectorSftpFileMetadata(){
        List<SftpFileMetadata> stub = List.of(FULL_SFTP_FILE_METADATA);
        when(sftpClient.ls(MY_PATH)).thenReturn(stub);
        var  res = sftp.listFiles(MY_PATH);
        assertTrue(res.length>0);
        assertNotNull(res);

        var actualRemoteFiles = Arrays.stream(res).iterator().next().getRemoteFile();
        assertEquals(FULL_SFTP_FILE_METADATA,actualRemoteFiles);
    }

    @Test
    public void listFiles_WithEmptyParameters_mustReturnVectorSftpFileMetadata(){

        when(sftpClient.ls(THIS_PATH)).thenReturn(List.of(FULL_SFTP_FILE_METADATA));
        var res = sftp.listFiles();
        verify(sftpClient).ls(THIS_PATH);

        var actualRemoteFiles = Arrays.stream(res).iterator().next().getRemoteFile();
        assertEquals(FULL_SFTP_FILE_METADATA,actualRemoteFiles);
    }

    @Test
    public void retrieveFile_shouldCorrectlyProcessGenericFile(){
        sftp.setEndpoint(endpoint);
        GenericFile<SftpFileMetadata> file = new GenericFile<>();
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);

        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(sftpClient.get(anyString())).thenReturn(InputStream.nullInputStream());

        when(exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE))
                .thenReturn(file);

        assertTrue(sftp.retrieveFile(TEST_FILENAME,exchange,-1));
        verify(sftpClient).get(anyString());
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
        sftp.storeFileDirectly(TEST_FILENAME,payload);
        verify(sftpClient).put(eq(TEST_FILENAME),any(ByteArrayInputStream.class));
    }

    @Test
    public void exists_shouldReturnTrue_whenFileExists(){
        sftp.setEndpoint(endpoint);
        when(sftpClient.ls(THIS_PATH)).thenReturn(List.of(FULL_SFTP_FILE_METADATA));

        assertTrue(sftp.existsFile(TEST_FILENAME));
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