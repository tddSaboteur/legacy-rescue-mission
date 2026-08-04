package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Proxy;
import org.apache.camel.component.file.remote.SftpConfiguration;

public record SessionContext(JSch jsch, SftpConfiguration sftpConfig, String certKeyType, Proxy proxy) {
}