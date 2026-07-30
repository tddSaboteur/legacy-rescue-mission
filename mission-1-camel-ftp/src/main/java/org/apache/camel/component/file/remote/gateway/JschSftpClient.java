package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.*;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;

import org.apache.camel.spi.CamelLogger;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.Charset;

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



    // Методы создания и конфигурирования channel
    public void channelConnect() throws JSchException {
        channel.connect();
    }
    public void chanenlSetBulkRequsets(Integer bulkRequests) throws JSchException {
        if (bulkRequests != null) {
            LOG.trace("configuring channel to use up to {} bulk request(s)", bulkRequests);

            channel.setBulkRequests(bulkRequests);
        }
    }
    public void openChannel() throws JSchException {
        channel = (ChannelSftp) session.openChannel("sftp");
    }
    public void channelSetFilenameEncoding(Charset ch) {
        channel.setFilenameEncoding(ch);
    }
    public void channelSetTimeout(int timeout) throws JSchException {
        channel.connect(timeout);
    }

    public ChannelSftp getChannel() {
        return channel;
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
    public void channelRm(String name) throws SftpException {
        channel.rm(name);
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
    public void initSession(Session session, int connectTimeout) throws JSchException {
        if (session == null || !session.isConnected()) {
            LOG.trace("Session isn't connected, trying to recreate and connect.");

            this.session = session;

            if (connectTimeout > 0) {
                LOG.trace("Connecting use connectTimeout: {} ...", connectTimeout);
                session.connect(connectTimeout);
            } else {
                LOG.trace("Connecting ...");
                session.connect();
            }
        }
    }
    public Session createSession(RemoteFileConfiguration configuration) throws JSchException {
        return jsch.getSession(configuration.getUsername(), configuration.getHost(), configuration.getPort());
    }






    //todo сейчас в методы нужно передавать сессию иначе сломаем многопоточку, в дальнейшем уберем в билдер или фабрику
    public void setSessionConfig(Session session, String key, String value) {
        session.setConfig(key,value);
    }
    public void configAliveSession(Session session, int interval, int count) throws JSchException {
        session.setServerAliveInterval(interval);
        session.setServerAliveCountMax(count);
    }
    public void configSesionUserInfo(Session session, CamelLogger messageLogger, String password, boolean isAutoCreateKnownHostsFile) {
        ExtendedUserInfo userInfo = createUserInfo(messageLogger, password, isAutoCreateKnownHostsFile);
        session.setUserInfo(userInfo);
    }
    public void setSessionTimeout(Session session, int timeout) throws JSchException {
        session.setTimeout(timeout);
    }
    public void sesionSetProxy(Session session) {
        if (proxy != null) {
            session.setProxy(proxy);
        }
    }
    public void configureSessionSocketFactory(Session session, String bindAddress) {
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
        SocketFactory socketFactory =new SocketFactory() {

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
    public void createJsch(LoggingLevel jschLoggingLevel) {
        JSch.setLogger(new JSchLogger(jschLoggingLevel));
        jsch = new JSch();
    }

    public void setJSchGlobalCiphersAndKex(String ciphers, String keyExchangeProtocols) {

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
    public String getJSchPubkeyAcceptedAlgorithms() {
        return JSch.getConfig("PubkeyAcceptedAlgorithms");
    }

    public void configureJSchIdentity(String name, byte[] prvKey, byte[] pubKey, byte[] passphrase) throws JSchException {
        jsch.addIdentity(name, prvKey, pubKey, passphrase);
    }

    public void configureJSchIdentity(String prvKey, byte[] passphrase) throws JSchException {
        jsch.addIdentity(prvKey, passphrase);
    }

    public void configureJSchKnownHost(String sftpConfig) throws JSchException {
        jsch.setKnownHosts(sftpConfig);
    }

    public void configureJSchKnownHost(InputStream is) throws JSchException {
        jsch.setKnownHosts(is);
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
