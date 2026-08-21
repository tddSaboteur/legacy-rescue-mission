package org.apache.camel.component.file.remote.gateway;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.remote.BaseSftpConfiguration;
import org.apache.camel.component.file.remote.SftpConfiguration;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public class SftpSecurityProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SftpSecurityProvider.class);
    private final CamelContext camelContext;

    public SftpSecurityProvider(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public byte[] resolveCertificateBytes(BaseSftpConfiguration config) throws SftpClientException {
        if (isNotEmpty(config.getCertFile())) {
            return loadCertFromFile(config.getCertFile());
        }
        if (isNotEmpty(config.getCertUri())) {
            return loadCertFromUri(config.getCertUri());
        }
        if (config.getCertBytes() != null) {
            return config.getCertBytes();
        }
        return null;
    }

    private byte[] loadCertFromFile(String filePath) {
        try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(
                camelContext, "file:" + filePath)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOHelper.copyAndCloseInput(is, bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new SftpClientException("Cannot read certificate file: " + filePath, e);
        }
    }

    private byte[] loadCertFromUri(String certUri) {
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

    public InputStream loadKnownHostsIS(String knownHostsUri) {
        InputStream knownHostIS;
        LOG.debug("Using known hosts uri: {}", knownHostsUri);
        try {
            knownHostIS = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext,
                    knownHostsUri);

        } catch (IOException e) {
            throw new SftpClientException("Cannot read resource: " + knownHostsUri, e);
        }
        return knownHostIS;
    }
}
