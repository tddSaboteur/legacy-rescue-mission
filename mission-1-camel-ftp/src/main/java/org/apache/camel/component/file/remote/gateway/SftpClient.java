package org.apache.camel.component.file.remote.gateway;

import org.apache.camel.component.file.remote.exception.SftpClientException;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

public interface SftpClient {
    //методы работы sftp
    void channelRm(String name) throws SftpClientException;

    void channelRename(String from, String to) throws SftpClientException;

    void channelMkdir(String directory) throws SftpClientException;

    String channelPwd() throws SftpClientException;

    void channelCd(String path) throws SftpClientException;

    Vector<?> channelLs(String path) throws SftpClientException;

    void channelLsByBreakSelector(String directory) throws SftpClientException;

    void channelChmod(String directory, int permissions) throws SftpClientException;

    void channelPutModeAppend(String targetName, InputStream is) throws SftpClientException;

    void channelPut(String targetName, InputStream is) throws SftpClientException;

    void channelGet(String remoteName, OutputStream os) throws SftpClientException;

    InputStream channelGet(String remoteName) throws SftpClientException;

    //инициализация
    void initSftpClient(JschSetup jschSetup);

    //Запрос состояние
    boolean isConnected();

    //Изменение состояния
    void disconnectSftp();

    void channelForceDisconnect();

    boolean sessionSendKeepAliveMsg();
}
