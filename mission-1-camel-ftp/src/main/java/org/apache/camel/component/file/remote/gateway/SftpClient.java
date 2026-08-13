package org.apache.camel.component.file.remote.gateway;

import org.apache.camel.component.file.remote.exception.SftpClientException;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

public interface SftpClient {
    //методы работы sftp
    void rm(String name) throws SftpClientException;

    void channelRename(String from, String to) throws SftpClientException;

    void mkdir(String directory) throws SftpClientException;

    String pwd() throws SftpClientException;

    void cd(String path) throws SftpClientException;

    Vector<?> ls(String path) throws SftpClientException;

    void lsByBreakSelector(String directory) throws SftpClientException;

    void chmod(String directory, int permissions) throws SftpClientException;

    void putModeAppend(String targetName, InputStream is) throws SftpClientException;

    void put(String targetName, InputStream is) throws SftpClientException;

    void get(String remoteName, OutputStream os) throws SftpClientException;

    InputStream get(String remoteName) throws SftpClientException;

    //инициализация
    void init(JschSetup jschSetup);

    //Запрос состояние
    boolean isConnected();

    //Изменение состояния
    void disconnectSftp();

    void forceDisconnect();

    boolean sendKeepAliveMsg();
}
