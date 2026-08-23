package org.apache.camel.component.file.remote.gateway;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.remote.SftpConfiguration;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SftpSecurityProviderTest {

    private static final String PRIVATE_KEY_PATH = "id_rsa";
    public static final String CERT_EXAMPLE = "cert_host_ca.pub";
    SftpSecurityProvider securityProvider;
    @Mock
    SftpConfiguration configuration;
    CamelContext camelContext;

    @BeforeEach
    void setUp() {
        camelContext = new DefaultCamelContext();
        securityProvider = new SftpSecurityProvider(camelContext);
    }

    @Test
    void load_WhenConfigEmpty_ShouldReturnNull() {
        assertNull(securityProvider.resolveCertificateBytes(configuration));
        assertNull(securityProvider.loadKnownHostsIS(null));
        assertNull(securityProvider.loadPrivateKey(null));
        assertNull(securityProvider.calculateCertKeyType(null,null));
    }

    @Test
    public void loadPrivateKey_widthPrivateKeyNotNull() throws IOException {
        byte[] expected;

        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream(PRIVATE_KEY_PATH)) {

            expected = input.readAllBytes();
        }
        assertArrayEquals(expected,securityProvider.loadPrivateKey(PRIVATE_KEY_PATH));
    }

    @Test
    public void resolveCertificate_widthCertificateFileNotNull() {
        var uri = getClass().getClassLoader().getResource(CERT_EXAMPLE);
        when(configuration.getCertFile()).thenReturn(uri.getPath());
        assertNotNull(securityProvider.resolveCertificateBytes(configuration));
    }
    @Test
    public void resolveCertificate_widthCertificateUriNotNull() {
        var uri = getClass().getClassLoader().getResource(CERT_EXAMPLE);
        when(configuration.getCertUri()).thenReturn(uri.toString());
        assertNotNull(securityProvider.resolveCertificateBytes(configuration));
    }

    @Test
    public void resolveCertificate_widthCertBytesNotNull() throws IOException {
        byte[] expected;

        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream(CERT_EXAMPLE)) {

            expected = input.readAllBytes();
        }
        when(configuration.getCertBytes()).thenReturn(expected);

        assertArrayEquals(expected,securityProvider.resolveCertificateBytes(configuration));
    }
    @Test
    public void loadKnownHostsIS(){
        assertNotNull(securityProvider.loadKnownHostsIS("known_hosts"));
    }
    @Test
    public void calculateCertKeyType_widthPublicKeyAcceptedAlgorithmsIsNotNull_ShouldReturnNull() throws IOException {
        byte[] certData;
        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream(CERT_EXAMPLE)) {

            certData = input.readAllBytes();
        }
        assertNull(securityProvider.calculateCertKeyType("PublicKeyAcceptedAlgorithms",certData));
    }
    @Test
    public void calculateCertKeyType_widthPublicKeyAcceptedAlgorithmsIsNull_ShouldReturnKeyTypeSshRsaCert() throws IOException {
        byte[] cert = """
            ssh-rsa-cert AAAA123
            """.getBytes(StandardCharsets.UTF_8);
        assertNotNull(securityProvider.calculateCertKeyType(null,cert));
    }
    @Test
    public void calculateCertKeyType_widthPublicKeyAcceptedAlgorithmsIsNull_ShouldReturnAlgorithmsKeyType() throws IOException {
        byte[] cert = """
            ssh-ed25519-cert-v01@openssh.com AAAA123
            """.getBytes(StandardCharsets.UTF_8);
        assertNotNull(securityProvider.calculateCertKeyType(null,cert));
    }
}