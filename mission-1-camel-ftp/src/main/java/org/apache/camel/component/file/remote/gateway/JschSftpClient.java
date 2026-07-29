package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.*;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.remote.RemoteFileConfiguration;

import org.apache.camel.component.file.remote.SftpOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class JschSftpClient {

    private static final Logger LOG = LoggerFactory.getLogger(JschSftpClient.class);
    private Session session;
    private JSch jsch;

    public ChannelSftp openChannel() throws JSchException {
        return (ChannelSftp) session.openChannel("sftp");
    }

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
    public void sesionSetUserInfo(Session session, SftpOperations.ExtendedUserInfo userInfo) {
        session.setUserInfo(userInfo);
    }
    public void setSessionTimeout(Session session, int timeout) throws JSchException {
        session.setTimeout(timeout);
    }
    public void sesionSetProxy(Session session, Proxy proxy) {
        session.setProxy(proxy);
    }
    public void setSessionSocketFactory(Session session, SocketFactory socketFactory) {
        session.setSocketFactory(socketFactory);
    }
    //todo end





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
