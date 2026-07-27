package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public JSch createJsch() {
        return new JSch();
    }
}
