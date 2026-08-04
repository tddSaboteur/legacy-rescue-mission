package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.*;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;

import org.apache.camel.component.file.remote.SftpConfiguration;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.util.HomeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
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

    //todo Лучше вынести в параметры сесии
    public JschSftpClient(Proxy proxy) {
        this.proxy = proxy;
    }

    //todo временный для доступа к Channel
    public ChannelSftp getChannel() {
        return channel;
    }


    // Методы создания и конфигурирования channel
    public void channelConnect() throws SftpClientException {
        try {
            channel.connect();
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка соединения канала.", e);
        }
    }

    public void channelConnectWidthTimeout(int timeout) throws SftpClientException {
        try {
            channel.connect(timeout);
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка соединения канала с использованием таймаута.", e);
        }
    }

    public void chanenlSetBulkRequsets(Integer bulkRequests) throws SftpClientException {
        if (bulkRequests != null) {
            LOG.trace("configuring channel to use up to {} bulk request(s)", bulkRequests);

            try {
                channel.setBulkRequests(bulkRequests);
            } catch (JSchException e) {
                throw new SftpClientException("Ошибка установки количества одновременных запросов.", e);
            }
        }
    }

    public void openChannel() throws SftpClientException {
        try {
            channel = (ChannelSftp) session.openChannel("sftp");
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка открытия канала.", e);
        }
    }

    public void channelSetFilenameEncoding(Charset ch) {
        channel.setFilenameEncoding(ch);
    }

    public boolean isConnectedChannel() {
        return channel != null && channel.isConnected();
    }

    //управление жизненым циклом channel
    public void disconnectChannel() {
        if (isConnectedChannel()) {
            channel.disconnect();
        }
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

    private SftpClientException generateCommandException(String command, SftpException cause) throws SftpClientException {
        return new SftpClientException("Ошибка выполнения команды:%s".formatted(command), cause, cause.id);
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


    // методы управления сессиями
    public boolean isConnectedSession() {
        return session != null && session.isConnected();
    }

    public void disconnectSession() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
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

    public void initSession( int connectTimeout) throws SftpClientException {
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

    public void createSession(SftpConfiguration sftpConfig, String certKeyType) throws SftpClientException {
        try {
            Session session = jsch.getSession(sftpConfig.getUsername(), sftpConfig.getHost(), sftpConfig.getPort());
            if (isNotEmpty(sftpConfig.getStrictHostKeyChecking())) {
                LOG.debug("Using StrictHostKeyChecking: {}", sftpConfig.getStrictHostKeyChecking());
                setSessionConfig(session, "StrictHostKeyChecking", sftpConfig.getStrictHostKeyChecking());
            }

            configAliveSession(session, sftpConfig.getServerAliveInterval(), sftpConfig.getServerAliveCountMax());
            // compression
            if (sftpConfig.getCompression() > 0) {
                LOG.debug("Using compression: {}", sftpConfig.getCompression());
                setSessionConfig(session, "compression.s2c", "zlib@openssh.com,zlib,none");
                setSessionConfig(session, "compression.c2s", "zlib@openssh.com,zlib,none");
                setSessionConfig(session, "compression_level", Integer.toString(sftpConfig.getCompression()));
            }

            // set the PreferredAuthentications
            if (sftpConfig.getPreferredAuthentications() != null) {
                LOG.debug("Using PreferredAuthentications: {}", sftpConfig.getPreferredAuthentications());
                setSessionConfig(session, "PreferredAuthentications", sftpConfig.getPreferredAuthentications());
            }
            // set the ServerHostKeys
            if (sftpConfig.getServerHostKeys() != null) {
                LOG.debug("Using ServerHostKeys: {}", sftpConfig.getServerHostKeys());
                setSessionConfig(session, "server_host_key", sftpConfig.getServerHostKeys());
            }
            // set the PublicKeyAcceptedAlgorithms
            if (sftpConfig.getPublicKeyAcceptedAlgorithms() != null) {
                LOG.debug("Using PublicKeyAcceptedAlgorithms: {}", sftpConfig.getPublicKeyAcceptedAlgorithms());
                setSessionConfig(session, "PubkeyAcceptedAlgorithms", sftpConfig.getPublicKeyAcceptedAlgorithms());
            }
            // set the CASignatureAlgorithms
            if (sftpConfig.getCaSignatureAlgorithms() != null) {
                LOG.debug("Using CASignatureAlgorithms: {}", sftpConfig.getCaSignatureAlgorithms());
                setSessionConfig(session, "ca_signature_algorithms", sftpConfig.getCaSignatureAlgorithms());
            }
            if (certKeyType != null) {
                String defaults = getJSchPubkeyAcceptedAlgorithms();
                if (defaults != null && !defaults.contains(certKeyType)) {
                    setSessionConfig(session, "PubkeyAcceptedAlgorithms", certKeyType + "," + defaults);
                    LOG.debug("Added certificate key type {} to PubkeyAcceptedAlgorithms", certKeyType);
                }
            }
            // set user information
            configSesionUserInfo(session,
                    new CamelLogger(LOG, ( sftpConfig).getServerMessageLoggingLevel()),
                    sftpConfig.getPassword(),
                    sftpConfig.isAutoCreateKnownHostsFile()
            );

            // set the SO_TIMEOUT for the time after the connect phase
            if (sftpConfig.getServerAliveInterval() == 0) {
                if (sftpConfig.getSoTimeout() > 0) {
                    setSessionTimeout(session, sftpConfig.getSoTimeout());
                }
            } else {
                LOG.debug(
                        "The Server Alive Internal is already set, the socket timeout won't be considered to avoid overidding the provided Server alive interval value");
            }

            if (isNotEmpty(sftpConfig.getBindAddress())) {

                configureSessionSocketFactory(session, sftpConfig.getBindAddress());
            }

            // set proxy if configured
            sesionSetProxy(session);
            this.session = session;

        } catch (JSchException e) {
            throw new SftpClientException("Ошибка получения сессии", e);
        }
    }

    private void setSessionConfig(Session session, String key, String value) {
        session.setConfig(key, value);
    }

    private void configAliveSession(Session session, int interval, int count) throws SftpClientException {
        try {
            session.setServerAliveInterval(interval);
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка настройки интервала сообщений поддержания соединения.", e);
        }
        session.setServerAliveCountMax(count);
    }

    private void configSesionUserInfo(Session session, CamelLogger messageLogger, String password, boolean isAutoCreateKnownHostsFile) {
        ExtendedUserInfo userInfo = createUserInfo(messageLogger, password, isAutoCreateKnownHostsFile);
        session.setUserInfo(userInfo);
    }

    private void setSessionTimeout(Session session, int timeout) throws SftpClientException {
        try {
            session.setTimeout(timeout);
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка настройки таймаута сессии.", e);
        }
    }

    private void sesionSetProxy(Session session) {
        if (proxy != null) {
            session.setProxy(proxy);
        }
    }

    private void configureSessionSocketFactory(Session session, String bindAddress) {
        session.setSocketFactory(createSocketFactory(session.getTimeout(), bindAddress));
    }
    //todo end


    private ExtendedUserInfo createUserInfo(CamelLogger messageLogger, String password, boolean isAutoCreateKnownHostsFile) {
        return new ExtendedUserInfo() {
            public String getPassphrase() {
                return null;
            }

            public String getPassword() {
                return password;
            }

            public boolean promptPassword(String s) {
                return true;
            }

            public boolean promptPassphrase(String s) {
                return true;
            }

            public boolean promptYesNo(String s) {
                // are we prompted because the known host files does not exist, and asked whether to auto-create the file
                boolean knownHostFile = s != null && s.endsWith("Are you sure you want to create it?");
                if (knownHostFile && isAutoCreateKnownHostsFile) {
                    LOG.warn("Server asks for confirmation (yes|no): {}. Camel will answer yes.", s);
                    return true;
                } else {
                    LOG.warn("Server asks for confirmation (yes|no): {}. Camel will answer no.", s);
                    // Return 'false' indicating modification of the hosts file is
                    // disabled.
                    return false;
                }
            }

            public void showMessage(String s) {
                messageLogger.log("FTP Server: " + s);
            }

            public String[] promptKeyboardInteractive(
                    String destination, String name, String instruction, String[] prompt, boolean[] echo) {
                // must return an empty array if password is null
                if (password == null) {
                    return new String[0];
                } else {
                    return new String[]{password};
                }
            }

        };
    }

    private SocketFactory createSocketFactory(int timeout, String bindAddress) {
        SocketFactory socketFactory = new SocketFactory() {

            @Override
            public OutputStream getOutputStream(Socket socket) throws IOException {
                return socket.getOutputStream();
            }

            @Override
            public InputStream getInputStream(Socket socket) throws IOException {
                return socket.getInputStream();
            }

            @Override
            public Socket createSocket(String host, int port) throws IOException {
                return createSocketUtil(host, port, bindAddress, timeout);
            }
        };
        return socketFactory;
    }

    /*
     * adapted from com.jcraft.jsch.Util.createSocket(String, int, int) added
     * possibility to specify the address of the local network interface,
     * against the connection should bind
     */
    static Socket createSocketUtil(final String host, final int port, final String bindAddress, final int timeout) {
        Socket socket;
        if (timeout == 0) {
            try {
                socket = new Socket(InetAddress.getByName(host), port, InetAddress.getByName(bindAddress), 0);
                return socket;
            } catch (Exception e) {
                String message = e.toString();
                throw new RuntimeCamelException(message, e);
            }
        }
        final Socket[] sockp = new Socket[1];
        final Exception[] ee = new Exception[1];
        String message = "";
        Thread tmp = new Thread(() -> {
            sockp[0] = null;
            try {
                sockp[0] = new Socket(InetAddress.getByName(host), port, InetAddress.getByName(bindAddress), 0);
            } catch (Exception e) {
                ee[0] = e;
                if (sockp[0] != null && sockp[0].isConnected()) {
                    IOHelper.close(sockp[0]);
                }
                sockp[0] = null;
            }
        });
        tmp.setName("Opening Socket " + host);
        tmp.start();
        try {
            tmp.join(timeout);
            message = "timeout: ";
        } catch (java.lang.InterruptedException eee) {
            Thread.currentThread().interrupt();
        }
        if (sockp[0] != null && sockp[0].isConnected()) {
            socket = sockp[0];
        } else {
            message += "socket is not established";
            if (ee[0] != null) {
                message = ee[0].toString();
            }
            tmp.interrupt();
            throw new RuntimeCamelException(message, ee[0]);
        }
        return socket;
    }


    public interface ExtendedUserInfo extends UserInfo, UIKeyboardInteractive {
    }


    //методы JSch
    public void createJsch(LoggingLevel jschLoggingLevel, SftpConfiguration sftpConfig, byte[] certData,byte[] privateKey, InputStream knownHosts) {
        JSch.setLogger(new JSchLogger(jschLoggingLevel));
        jsch = new JSch();
        setJSchGlobalCiphersAndKex(sftpConfig.getCiphers(), sftpConfig.getKeyExchangeProtocols());
        if (isNotEmpty(sftpConfig.getKnownHostsFile())) {
            LOG.debug("Using knownhosts file: {}", sftpConfig.getKnownHostsFile());
            configureJSchKnownHost(sftpConfig.getKnownHostsFile());
        }
        if (sftpConfig.getKnownHosts() != null) {
            LOG.debug("Using known hosts information from byte array");
            configureJSchKnownHost(new ByteArrayInputStream(sftpConfig.getKnownHosts()));
        }
        String knownHostsFile = sftpConfig.getKnownHostsFile();
        if (knownHostsFile == null && sftpConfig.isUseUserKnownHostsFile()) {
            knownHostsFile = HomeHelper.resolveHomeDir() + "/.ssh/known_hosts";
            LOG.info("Known host file not configured, using user known host file: {}", knownHostsFile);
        }
        if (ObjectHelper.isNotEmpty(knownHostsFile)) {
            LOG.debug("Using known hosts information from file: {}", knownHostsFile);
            configureJSchKnownHost(knownHostsFile);
        }

        if (isNotEmpty(sftpConfig.getPrivateKeyFile())) {
            LOG.debug("Using private keyfile: {}", sftpConfig.getPrivateKeyFile());
            byte[] passphrase = null;
            if (isNotEmpty(sftpConfig.getPrivateKeyPassphrase())) {
                passphrase = sftpConfig.getPrivateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
            }
            if (certData != null) {
                // Use byte-based overload for certificate — JSch's file-based
                // addIdentity(prvkey, pubkey, passphrase) treats the second parameter
                // as a public key file, not a certificate
                LOG.debug("Using OpenSSH certificate for authentication");
                try {
                    byte[] keyData = Files.readAllBytes(Paths.get(sftpConfig.getPrivateKeyFile()));
                    configureJSchIdentity(sftpConfig.getPrivateKeyFile(), keyData, certData, passphrase);
                } catch (IOException e) {
                    throw new SftpClientException("Cannot read private key file: " + sftpConfig.getPrivateKeyFile(), e);
                }
            } else {
                // No explicit cert — JSch auto-discovers <key>-cert.pub if it exists
                configureJSchIdentity(sftpConfig.getPrivateKeyFile(), passphrase);
            }
        }

        if (sftpConfig.getPrivateKey() != null) {
            LOG.debug("Using private key information from byte array");
            byte[] passphrase = null;
            if (isNotEmpty(sftpConfig.getPrivateKeyPassphrase())) {
                passphrase = sftpConfig.getPrivateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
            }
            configureJSchIdentity("ID", sftpConfig.getPrivateKey(), certData, passphrase);
        }

        if (sftpConfig.getKeyPair() != null) {
            LOG.debug("Using private key information from key pair");
            KeyPair keyPair = sftpConfig.getKeyPair();
            if (keyPair.getPrivate() != null) {
                // Encode the private key in PEM format for JSCH
                StringBuilder sb = new StringBuilder(256);
                sb.append("-----BEGIN PRIVATE KEY-----").append("\n");
                sb.append(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())).append("\n");
                sb.append("-----END PRIVATE KEY-----").append("\n");

                configureJSchIdentity("ID", sb.toString().getBytes(StandardCharsets.UTF_8), certData, null);
            } else {
                LOG.warn("PrivateKey in the KeyPair must be filled");
            }
        }
        byte[] passphrase = null;
        if (isNotEmpty(sftpConfig.getPrivateKeyPassphrase())) {
            passphrase = sftpConfig.getPrivateKeyPassphrase().getBytes(StandardCharsets.UTF_8);
        }
        if (privateKey != null){
            configureJSchIdentity("ID", privateKey, certData, passphrase);
        }
        if (knownHosts != null) {
            configureJSchKnownHost(knownHosts);
        }
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

    private String getJSchPubkeyAcceptedAlgorithms() {
        return JSch.getConfig("PubkeyAcceptedAlgorithms");
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
