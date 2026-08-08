package org.apache.camel.component.file.remote.gateway;

import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.remote.SftpConfiguration;

import java.io.InputStream;

public record JschSetup( SftpConfiguration sftpConfig, byte[] certData, byte[] privateKey,
                        InputStream knownHosts) {
}