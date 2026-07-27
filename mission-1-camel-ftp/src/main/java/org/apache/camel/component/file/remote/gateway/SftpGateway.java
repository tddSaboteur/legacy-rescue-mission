package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.remote.SftpConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;

public class SftpGateway {

    private static final Logger LOG = LoggerFactory.getLogger(SftpGateway.class);
    private Session session;

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

    public JSch createJsch(LoggingLevel jschLoggingLevel) {
        JSch.setLogger(new JSchLogger(jschLoggingLevel));
        return new JSch();
    }

    public void setGlobalCiphersAndKex(String ciphers, String keyExchangeProtocols) {

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
