package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.SocketFactory;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.IOHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class JschSocketFactory implements SocketFactory {

    private final String bindAddress;
    private final int timeout;

    public JschSocketFactory(String bindAddress, int timeout) {
        this.bindAddress = bindAddress;
        this.timeout = timeout;
    }


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

}
