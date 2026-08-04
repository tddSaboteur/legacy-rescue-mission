package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.*;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public class JschSessionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(JschSessionFactory.class);

    public Session createSession(SessionContext sessionContext) throws JSchException {
        try {
            Session session = sessionContext.jsch().getSession(sessionContext.sftpConfig().getUsername(), sessionContext.sftpConfig().getHost(), sessionContext.sftpConfig().getPort());
            if (isNotEmpty(sessionContext.sftpConfig().getStrictHostKeyChecking())) {
                LOG.debug("Using StrictHostKeyChecking: {}", sessionContext.sftpConfig().getStrictHostKeyChecking());
                setSessionConfig(session, "StrictHostKeyChecking", sessionContext.sftpConfig().getStrictHostKeyChecking());
            }

            configAliveSession(session, sessionContext.sftpConfig().getServerAliveInterval(), sessionContext.sftpConfig().getServerAliveCountMax());
            // compression
            if (sessionContext.sftpConfig().getCompression() > 0) {
                LOG.debug("Using compression: {}", sessionContext.sftpConfig().getCompression());
                setSessionConfig(session, "compression.s2c", "zlib@openssh.com,zlib,none");
                setSessionConfig(session, "compression.c2s", "zlib@openssh.com,zlib,none");
                setSessionConfig(session, "compression_level", Integer.toString(sessionContext.sftpConfig().getCompression()));
            }

            // set the PreferredAuthentications
            if (sessionContext.sftpConfig().getPreferredAuthentications() != null) {
                LOG.debug("Using PreferredAuthentications: {}", sessionContext.sftpConfig().getPreferredAuthentications());
                setSessionConfig(session, "PreferredAuthentications", sessionContext.sftpConfig().getPreferredAuthentications());
            }
            // set the ServerHostKeys
            if (sessionContext.sftpConfig().getServerHostKeys() != null) {
                LOG.debug("Using ServerHostKeys: {}", sessionContext.sftpConfig().getServerHostKeys());
                setSessionConfig(session, "server_host_key", sessionContext.sftpConfig().getServerHostKeys());
            }
            // set the PublicKeyAcceptedAlgorithms
            if (sessionContext.sftpConfig().getPublicKeyAcceptedAlgorithms() != null) {
                LOG.debug("Using PublicKeyAcceptedAlgorithms: {}", sessionContext.sftpConfig().getPublicKeyAcceptedAlgorithms());
                setSessionConfig(session, "PubkeyAcceptedAlgorithms", sessionContext.sftpConfig().getPublicKeyAcceptedAlgorithms());
            }
            // set the CASignatureAlgorithms
            if (sessionContext.sftpConfig().getCaSignatureAlgorithms() != null) {
                LOG.debug("Using CASignatureAlgorithms: {}", sessionContext.sftpConfig().getCaSignatureAlgorithms());
                setSessionConfig(session, "ca_signature_algorithms", sessionContext.sftpConfig().getCaSignatureAlgorithms());
            }
            if (sessionContext.certKeyType() != null) {
                String defaults = getJSchPubkeyAcceptedAlgorithms();
                if (defaults != null && !defaults.contains(sessionContext.certKeyType())) {
                    setSessionConfig(session, "PubkeyAcceptedAlgorithms", sessionContext.certKeyType() + "," + defaults);
                    LOG.debug("Added certificate key type {} to PubkeyAcceptedAlgorithms", sessionContext.certKeyType());
                }
            }
            // set user information
            configSessionUserInfo(session,
                    new CamelLogger(LOG, (sessionContext.sftpConfig()).getServerMessageLoggingLevel()),
                    sessionContext.sftpConfig().getPassword(),
                    sessionContext.sftpConfig().isAutoCreateKnownHostsFile()
            );

            // set the SO_TIMEOUT for the time after the connect phase
            if (sessionContext.sftpConfig().getServerAliveInterval() == 0) {
                if (sessionContext.sftpConfig().getSoTimeout() > 0) {
                    setSessionTimeout(session, sessionContext.sftpConfig().getSoTimeout());
                }
            } else {
                LOG.debug(
                        "The Server Alive Internal is already set, the socket timeout won't be considered to avoid overidding the provided Server alive interval value");
            }

            if (isNotEmpty(sessionContext.sftpConfig().getBindAddress())) {

                configureSessionSocketFactory(session, sessionContext.sftpConfig().getBindAddress());
            }

            // set proxy if configured
            sessionSetProxy(session, sessionContext.proxy());
            return session;

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

    private void configSessionUserInfo(Session session, CamelLogger messageLogger, String password, boolean isAutoCreateKnownHostsFile) {
        JschSftpClient.ExtendedUserInfo userInfo = createUserInfo(messageLogger, password, isAutoCreateKnownHostsFile);
        session.setUserInfo(userInfo);
    }
    private void setSessionTimeout(Session session, int timeout) throws SftpClientException {
        try {
            session.setTimeout(timeout);
        } catch (JSchException e) {
            throw new SftpClientException("Ошибка настройки таймаута сессии.", e);
        }
    }
    private void sessionSetProxy(Session session, Proxy proxy) {
        if (proxy != null) {
            session.setProxy(proxy);
        }
    }

    private void configureSessionSocketFactory(Session session, String bindAddress) {
        session.setSocketFactory(createSocketFactory(session.getTimeout(), bindAddress));
    }

    private JschSftpClient.ExtendedUserInfo createUserInfo(CamelLogger messageLogger, String password, boolean isAutoCreateKnownHostsFile) {
        return new JschSftpClient.ExtendedUserInfo() {
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
        return new SocketFactory() {

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
    }

    /*
     * adapted from com.jcraft.jsch.Util.createSocket(String, int, int) added
     * possibility to specify the address of the local network interface,
     * against the connection should bind
     */
    private Socket createSocketUtil(final String host, final int port, final String bindAddress, final int timeout) {
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

    private String getJSchPubkeyAcceptedAlgorithms() {
        return JSch.getConfig("PubkeyAcceptedAlgorithms");
    }
}
