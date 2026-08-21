package org.apache.camel.component.file.remote.gateway;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.file.remote.SftpConfiguration;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.impl.DefaultDumpRoutesStrategy;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SftpSecurityProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SftpSecurityProvider.class);
    private final CamelContext camelContext;

    public SftpSecurityProvider(CamelContext camelContext) {
        this.camelContext= camelContext;
    }

    public byte[] loadCertFromFile(String filePath) {
        try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(
                camelContext, "file:" + filePath)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOHelper.copyAndCloseInput(is, bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new SftpClientException("Cannot read certificate file: " + filePath, e);
        }
    }
    public byte[] loadCertFromUri(String certUri) {
        try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(
                camelContext, certUri)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOHelper.copyAndCloseInput(is, bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new SftpClientException("Cannot read certificate resource: " + certUri, e);
        }
    }
    public byte[] loadPrivateKey(SftpConfiguration sftpConfig) {
        byte[] privateKey;
        LOG.debug("Using private key uri : {}", sftpConfig.getPrivateKeyUri());

        try {
            InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext,
                    sftpConfig.getPrivateKeyUri());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOHelper.copyAndCloseInput(is, bos);
            privateKey = bos.toByteArray();

        } catch (IOException e) {
            throw new SftpClientException("Cannot read resource: " + sftpConfig.getPrivateKeyUri(), e);
        }
        return privateKey;
    }
}
