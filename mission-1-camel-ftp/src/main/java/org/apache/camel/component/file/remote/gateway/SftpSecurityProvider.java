package org.apache.camel.component.file.remote.gateway;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.remote.BaseSftpConfiguration;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public class SftpSecurityProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SftpSecurityProvider.class);
    private final CamelContext camelContext;

    public SftpSecurityProvider(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public SecurityMaterials resolve(BaseSftpConfiguration config) {
        byte[] certData = resolveCertificateBytes(config);
        return new SecurityMaterials(certData,
                loadPrivateKey(config.getPrivateKeyUri()),
                loadKnownHostsIS(config.getKnownHostsUri()),
                calculateCertKeyType(config.getPublicKeyAcceptedAlgorithms(),certData)
        );
    }

    private byte[] resolveCertificateBytes(BaseSftpConfiguration config) throws SftpClientException {
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
        try {
            return readResourceBytes("file:" + filePath);
        } catch (IOException e) {
            throw new SftpClientException("Cannot read certificate file: " + filePath, e);
        }
    }

    private byte[] loadCertFromUri(String certUri) {
        try {
            return readResourceBytes(certUri);
        } catch (IOException e) {
            throw new SftpClientException("Cannot read certificate resource: " + certUri, e);
        }
    }

    private byte[] loadPrivateKey(String privateKeyUri) {
        byte[] privateKey = null;
        if (privateKeyUri != null) {
            LOG.debug("Using private key uri : {}", privateKeyUri);
            try {
                privateKey = readResourceBytes(privateKeyUri);
            } catch (IOException e) {
                throw new SftpClientException("Cannot read resource: " + privateKeyUri, e);
            }
        }
        return privateKey;
    }

    private InputStream loadKnownHostsIS(String knownHostsUri) {
        InputStream knownHostIS = null;
        if (isNotEmpty(knownHostsUri)) {
            LOG.debug("Using known hosts uri: {}", knownHostsUri);
            try {
                knownHostIS = openResource(knownHostsUri);
            } catch (IOException e) {
                throw new SftpClientException("Cannot read resource: " + knownHostsUri, e);
            }
        }
        return knownHostIS;
    }

    private byte[] readResourceBytes(String uri) throws IOException {
        InputStream is = openResource(uri);
        return readByteOfInputStream(is);
    }

    private static byte[] readByteOfInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, bos);
        return bos.toByteArray();
    }

    private InputStream openResource(String knownHostsUri) throws IOException {
        return ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext,
                knownHostsUri);
    }

    // Auto-configure PubkeyAcceptedAlgorithms for certificate authentication.
    // JSch's defaults exclude SHA-1 based algorithms (matching OpenSSH 8.2+ policy),
    // which includes ssh-rsa-cert-v01@openssh.com (or ssh-rsa-cert per newer RFC
    // drafts). If the loaded certificate uses a key type not in the accepted list,
    // JSch silently skips the certificate identity and auth fails.
    // Detect the cert type and add it if missing.
    private String calculateCertKeyType(String publicKeyAcceptedAlgorithms, byte[] certData) {
        String certKeyType = null;
        if (publicKeyAcceptedAlgorithms == null) {
            certKeyType = detectCertKeyType(certData);

        }
        return certKeyType;
    }

    /**
     * Detects the OpenSSH certificate key type from the given certificate data. OpenSSH certificate files use text
     * format: "key-type base64-data [comment]".
     *
     * @return the certificate key type (e.g., "ssh-rsa-cert-v01@openssh.com" or "ssh-rsa-cert") or null
     */
    private static String detectCertKeyType(byte[] certData) {
        if (certData == null) {
            return null;
        }
        String certLine = new String(certData, StandardCharsets.UTF_8).trim();
        int space = certLine.indexOf(' ');
        if (space > 0) {
            String keyType = certLine.substring(0, space);
            if (
                // ssh key type format for rfc until draft 03
                // https://datatracker.ietf.org/doc/html/draft-miller-ssh-cert-03.html
                    keyType.endsWith("-cert-v01@openssh.com") ||
                            // ssh key type format for rfc from draft 04
                            // https://datatracker.ietf.org/doc/html/draft-miller-ssh-cert-04.html
                            keyType.endsWith("-cert")) {

                return keyType;
            }
        }
        return null;
    }
}