package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.*;
import org.apache.camel.LoggingLevel;

import org.apache.camel.component.file.remote.SftpConfiguration;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.util.HomeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Vector;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public class JschSftpClient {

    private static final Logger LOG = LoggerFactory.getLogger(JschSftpClient.class);
    private Session session;
    private JSch jsch;
    private Proxy proxy;
    private ChannelSftp channel;

    public JschSftpClient() {
    }

    public JschSftpClient(Proxy proxy) {
        this.proxy = proxy;
    }

    // Методы создания и конфигурирования channel


    public void channelSetBulkRequests(Integer bulkRequests) throws SftpClientException {
        if (bulkRequests != null) {
            LOG.trace("configuring channel to use up to {} bulk request(s)", bulkRequests);
            try {
                channel.setBulkRequests(bulkRequests);
            } catch (JSchException e) {
                throw new SftpClientException("Ошибка установки количества одновременных запросов.", e);
            }
        }
    }

    public boolean isConnectedChannel() {
        return channel != null && channel.isConnected();
    }

    public void channelForceDisconnect() {
        try {
            forceDisconnectSession();
            if (channel != null) {
                channel.disconnect();
            }
        } finally {
            // ensure these
            channel = null;

        }
    }

    //методы работы с channel
    public void channelRm(String name) throws SftpClientException {
        try {
            channel.rm(name);
        } catch (SftpException e) {
            throw generateCommandException("rm", e);
        }
    }

    public void channelRename(String from, String to) throws SftpClientException {
        try {
            channel.rename(from, to);
        } catch (SftpException e) {
            throw generateCommandException("rename", e);
        }
    }

    public void channelMkdir(String directory) throws SftpClientException {
        try {
            channel.mkdir(directory);
        } catch (SftpException e) {
            throw generateCommandException("mkdir", e);
        }
    }

    public String channelPwd() throws SftpClientException {
        try {
            return channel.pwd();
        } catch (SftpException e) {
            throw generateCommandException("pwd", e);
        }
    }

    public void channelCd(String path) throws SftpClientException {
        try {
            channel.cd(path);
        } catch (SftpException e) {
            throw generateCommandException("cd", e);
        }
    }

    public Vector<?> channelLs(String path) throws SftpClientException {
        try {
            return channel.ls(path);
        } catch (SftpException e) {
            // or an exception can be thrown with id 2 which means file does not
            // exists
            if (ChannelSftp.SSH_FX_NO_SUCH_FILE == e.id) {
                return null;
            }
            throw generateCommandException("ls", e);
        }
    }

    public void channelLsByBreakSelector(String directory) throws SftpClientException {
        try {
            channel.ls(directory, entry -> ChannelSftp.LsEntrySelector.BREAK);
        } catch (SftpException e) {
            throw generateCommandException("ls", e);
        }
    }

    public void channelChmod(String directory, int permissions) throws SftpClientException {
        try {
            channel.chmod(permissions, directory);
        } catch (SftpException e) {
            throw generateCommandException("chmod", e);
        }
    }

    public void channelPutModeAppend(String targetName, InputStream is) throws SftpClientException {
        try {
            channel.put(is, targetName, ChannelSftp.APPEND);
        } catch (SftpException e) {
            throw generateCommandException("put", e);
        }
    }

    public void channelPut(String targetName, InputStream is) throws SftpClientException {
        try {
            channel.put(is, targetName);
        } catch (SftpException e) {
            throw generateCommandException("put", e);
        }
    }




    public void channelGet(String remoteName, OutputStream os) throws SftpClientException {
        try {
            channel.get(remoteName, os);
        } catch (SftpException e) {
            generateCommandException("get", e);
        }
    }


    public InputStream channelGet(String remoteName) throws SftpClientException {
        try {
            return channel.get(remoteName);
        } catch (SftpException e) {
            throw generateCommandException("get", e);
        }
    }

    public void disconnectSftp() {
        disconnectSession();
        disconnectChannel();
    }

    public boolean isConnected(){
        return isConnectedSession() && isConnectedChannel();
    }

    public void forceDisconnectSession() {
        if (session != null) {
            session.disconnect();
        }
        session = null;
    }

    public boolean sessionSendKeepAliveMsg() {
        try {
            session.sendKeepAliveMsg();
            return true;
        } catch (Exception e) {
            LOG.debug("SFTP session was closed. Ignoring this exception.", e);
            return false;
        }
    }

    public void initSftpClient(JschSetup jschSetup) {
        JSch.setLogger(new JSchLogger(jschSetup.sftpConfig().getJschLoggingLevel()));
        jsch = new JSch();
        setJSchGlobalCiphersAndKex(jschSetup.sftpConfig().getCiphers(), jschSetup.sftpConfig().getKeyExchangeProtocols());
        if (isNotEmpty(jschSetup.sftpConfig().getKnownHostsFile())) {
            LOG.debug("Using knownhosts file: {}", jschSetup.sftpConfig().getKnownHostsFile());
            configureJSchKnownHost(jschSetup.sftpConfig().getKnownHostsFile());
        }
        if (jschSetup.sftpConfig().getKnownHosts() != null) {
            LOG.debug("Using known hosts information from byte array");
            configureJSchKnownHost(new ByteArrayInputStream(jschSetup.sftpConfig().getKnownHosts()));
        }
        String knownHostsFile = jschSetup.sftpConfig().getKnownHostsFile();
        if (knownHostsFile == null && jschSetup.sftpConfig().isUseUserKnownHostsFile()) {
            knownHostsFile = HomeHelper.resolveHomeDir() + "/.ssh/known_hosts";
            LOG.info("Known host file not configured, using user known host file: {}", knownHostsFile);
        }
        if (ObjectHelper.isNotEmpty(knownHostsFile)) {
            LOG.debug("Using known hosts information from file: {}", knownHostsFile);
            configureJSchKnownHost(knownHostsFile);
        }

        if (isNotEmpty(jschSetup.sftpConfig().getPrivateKeyFile())) {
            LOG.debug("Using private keyfile: {}", jschSetup.sftpConfig().getPrivateKeyFile());
            byte[] passphrase = null;
            if (isNotEmpty(jschSetup.sftpConfig().getPrivateKeyPassphrase())) {
                passphrase = jschSetup.sftpConfig().getPrivateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
            }
            if (jschSetup.certData() != null) {
                // Use byte-based overload for certificate — JSch's file-based
                // addIdentity(prvkey, pubkey, passphrase) treats the second parameter
                // as a public key file, not a certificate
                LOG.debug("Using OpenSSH certificate for authentication");
                try {
                    byte[] keyData = Files.readAllBytes(Paths.get(jschSetup.sftpConfig().getPrivateKeyFile()));
                    configureJSchIdentity(jschSetup.sftpConfig().getPrivateKeyFile(), keyData, jschSetup.certData(), passphrase);
                } catch (IOException e) {
                    throw new SftpClientException("Cannot read private key file: " + jschSetup.sftpConfig().getPrivateKeyFile(), e);
                }
            } else {
                // No explicit cert — JSch auto-discovers <key>-cert.pub if it exists
                configureJSchIdentity(jschSetup.sftpConfig().getPrivateKeyFile(), passphrase);
            }
        }

        if (jschSetup.sftpConfig().getPrivateKey() != null) {
            LOG.debug("Using private key information from byte array");
            byte[] passphrase = null;
            if (isNotEmpty(jschSetup.sftpConfig().getPrivateKeyPassphrase())) {
                passphrase = jschSetup.sftpConfig().getPrivateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
            }
            configureJSchIdentity("ID", jschSetup.sftpConfig().getPrivateKey(), jschSetup.certData(), passphrase);
        }

        if (jschSetup.sftpConfig().getKeyPair() != null) {
            LOG.debug("Using private key information from key pair");
            KeyPair keyPair = jschSetup.sftpConfig().getKeyPair();
            if (keyPair.getPrivate() != null) {
                // Encode the private key in PEM format for JSCH
                StringBuilder sb = new StringBuilder(256);
                sb.append("-----BEGIN PRIVATE KEY-----").append("\n");
                sb.append(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())).append("\n");
                sb.append("-----END PRIVATE KEY-----").append("\n");

                configureJSchIdentity("ID", sb.toString().getBytes(StandardCharsets.UTF_8), jschSetup.certData(), null);
            } else {
                LOG.warn("PrivateKey in the KeyPair must be filled");
            }
        }
        byte[] passphrase = null;
        if (isNotEmpty(jschSetup.sftpConfig().getPrivateKeyPassphrase())) {
            passphrase = jschSetup.sftpConfig().getPrivateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
        }
        if (jschSetup.privateKey() != null){
            configureJSchIdentity("ID", jschSetup.privateKey(), jschSetup.certData(), passphrase);
        }
        if (jschSetup.knownHosts() != null) {
            configureJSchKnownHost(jschSetup.knownHosts());
        }
        createSession(jschSetup.sftpConfig(), jschSetup.certKeyType());
        LOG.trace("Channel isn't connected, trying to recreate and connect.");
        openChannel(jschSetup.sftpConfig().getFilenameEncoding(), jschSetup.sftpConfig().getConnectTimeout());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Connected to {}", jschSetup.sftpConfig().remoteServerInformation());
        }
    }


    private void channelConnect() throws SftpClientException {
        try {
            channel.connect();
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка соединения канала.", e);
        }
    }

    private void channelConnectWidthTimeout(int timeout) throws SftpClientException {
        try {
            channel.connect(timeout);
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка соединения канала с использованием таймаута.", e);
        }
    }

    private void openChannel(String filenameEncoding, int connectTimeout) throws SftpClientException {
        try {
            channel = (ChannelSftp) session.openChannel("sftp");
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка открытия канала.", e);
        }
        if (filenameEncoding != null) {
            Charset ch = Charset.forName(filenameEncoding);
            LOG.trace("Using filename encoding: {}", ch);
            channelSetFilenameEncoding(ch);
        }

        if (connectTimeout > 0) {
            LOG.trace("Connecting use connectTimeout: {} ...", connectTimeout);
            channelConnectWidthTimeout(connectTimeout);
        } else {
            LOG.trace("Connecting ...");
            channelConnect();
        }

    }
    private void channelSetFilenameEncoding(Charset ch) {
        channel.setFilenameEncoding(ch);
    }
    private void disconnectChannel() {
        if (isConnectedChannel()) {
            channel.disconnect();
        }
    }
    private boolean isConnectedSession() {
        return session != null && session.isConnected();
    }



    private SftpClientException generateCommandException(String command, SftpException cause) throws SftpClientException {
        return new SftpClientException("Ошибка выполнения команды:%s".formatted(command), cause, cause.id);
    }

    private void disconnectSession() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    private void initSession( int connectTimeout) throws SftpClientException {
        if (session == null || !session.isConnected()) {
            LOG.trace("Session isn't connected, trying to recreate and connect.");


            try {
                if (connectTimeout > 0) {
                    LOG.trace("Connecting use connectTimeout: {} ...", connectTimeout);

                    session.connect(connectTimeout);

                } else {
                    LOG.trace("Connecting ...");
                    session.connect();
                }
            } catch (JSchException e) {
                throw new SftpClientException("Ошибка создания соединения сессией",e);
            }
        }
    }

    private void createSession(SftpConfiguration sftpConfig, String certKeyType) throws SftpClientException {
        JschSessionFactory sessionFactory = new JschSessionFactory();
        try {
            this.session = sessionFactory.createSession(new SessionContext(jsch, sftpConfig, certKeyType, proxy));
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка создания сессии",e);
        }

        initSession(sftpConfig.getConnectTimeout());

    }

    private void setJSchGlobalCiphersAndKex(String ciphers, String keyExchangeProtocols) {

        if (ciphers != null && !ciphers.isEmpty()) {
            LOG.debug("Using ciphers: {}", ciphers);
            java.util.Hashtable<String, String> ciphersMap = new java.util.Hashtable<>();
            ciphersMap.put("cipher.s2c", ciphers);
            ciphersMap.put("cipher.c2s", ciphers);
            JSch.setConfig(ciphersMap);
        }

        if (keyExchangeProtocols != null && !keyExchangeProtocols.isEmpty()) {
            LOG.debug("Using KEX: {}", keyExchangeProtocols);
            JSch.setConfig("kex", keyExchangeProtocols);
        }
    }

    private void configureJSchIdentity(String name, byte[] prvKey, byte[] pubKey, byte[] passphrase) throws SftpClientException {
        try {
            jsch.addIdentity(name, prvKey, pubKey, passphrase);
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка настройки аутентификации", e);
        }
    }

    private void configureJSchIdentity(String prvKey, byte[] passphrase) throws SftpClientException {
        try {
            jsch.addIdentity(prvKey, passphrase);
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка настройки аутентификации", e);
        }
    }

    private void configureJSchKnownHost(String sftpConfig) throws SftpClientException {
        try {
            jsch.setKnownHosts(sftpConfig);
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка настройки известных хостов", e);
        }
    }

    private void configureJSchKnownHost(InputStream is) throws SftpClientException {
        try {
            jsch.setKnownHosts(is);
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка настройки известных хостов", e);
        }
    }
    private static final class JSchLogger implements com.jcraft.jsch.Logger {



        private final LoggingLevel loggingLevel;

        private JSchLogger(LoggingLevel loggingLevel) {
            this.loggingLevel = loggingLevel;
        }

        @Override
        public boolean isEnabled(int level) {
            switch (level) {
                case FATAL:
                    // use ERROR as FATAL
                    return loggingLevel.isEnabled(LoggingLevel.ERROR) && LOG.isErrorEnabled();
                case ERROR:
                    return loggingLevel.isEnabled(LoggingLevel.ERROR) && LOG.isErrorEnabled();
                case WARN:
                    return loggingLevel.isEnabled(LoggingLevel.WARN) && LOG.isWarnEnabled();
                case INFO:
                    return loggingLevel.isEnabled(LoggingLevel.INFO) && LOG.isInfoEnabled();
                default:
                    return loggingLevel.isEnabled(LoggingLevel.DEBUG) && LOG.isDebugEnabled();
            }
        }

        @Override
        public void log(int level, String message) {
            switch (level) {
                case FATAL:
                    // use ERROR as FATAL
                    LOG.error("JSCH -> {}", message);
                    break;
                case ERROR:
                    LOG.error("JSCH -> {}", message);
                    break;
                case WARN:
                    LOG.warn("JSCH -> {}", message);
                    break;
                case INFO:
                    LOG.info("JSCH -> {}", message);
                    break;
                default:
                    LOG.debug("JSCH -> {}", message);
                    break;
            }
        }
    }
}
