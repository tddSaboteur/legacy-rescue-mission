package org.apache.camel.component.file.remote.gateway;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.remote.SftpConfiguration;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.impl.DefaultCamelContext;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SftpSecurityProviderTest {

    private static final String PRIVATE_KEY_PATH = "id_rsa";
    public static final String CERT_EXAMPLE = "cert_host_ca.pub";
    public static final String INCORRECT_PATH = "INCORRECT PATH";
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
    void resolve_WhenConfigEmpty_ShouldReturnEmptySecurityMaterial() {
        var material = securityProvider.resolve(configuration);

        assertNull(material.certificate());
        assertNull(material.privateKey());
        assertNull(material.knownHostsIS());
        assertNull(material.certKeyType());
    }

    @Test
    void load_WhenConfigIncorrectCertPath_ShouldThrowException() {

        when(configuration.getCertFile()).thenReturn(INCORRECT_PATH);
        Exception exception = assertThrows(SftpClientException.class, () -> securityProvider.resolve(configuration));
        assertEquals("Cannot read certificate file: " + INCORRECT_PATH, exception.getMessage());
    }


    @Test
    void load_WhenConfigIncorrectCertUri_ShouldThrowException() {
        when(configuration.getCertUri()).thenReturn(INCORRECT_PATH);
        Exception exception = assertThrows(SftpClientException.class, () -> securityProvider.resolve(configuration));
        assertEquals("Cannot read certificate resource: " + INCORRECT_PATH, exception.getMessage());

    }

    @Test
    void load_WhenConfigIncorrectKnownHostsPath_ShouldThrowException() {
        when(configuration.getKnownHostsUri()).thenReturn(INCORRECT_PATH);
        Exception exception = assertThrows(SftpClientException.class, () -> securityProvider.resolve(configuration));
        assertEquals("Cannot read KnownHostsIS resource: " + INCORRECT_PATH, exception.getMessage());
    }

    @Test
    void load_WhenConfigIncorrectPrivateKeyPath_ShouldThrowException() {
        when(configuration.getPrivateKeyUri()).thenReturn(INCORRECT_PATH);
        Exception exception = assertThrows(SftpClientException.class, () -> securityProvider.resolve(configuration));
        assertEquals("Cannot read PrivateKey resource: " + INCORRECT_PATH, exception.getMessage());
    }



    @Test
    public void resolve_WhenPrivateKeyConfigured_ShouldLoadPrivateKey() throws IOException {
        byte[] expected = readBytes(PRIVATE_KEY_PATH);
        when(configuration.getPrivateKeyUri()).thenReturn(PRIVATE_KEY_PATH);
        assertArrayEquals(expected, securityProvider.resolve(configuration).privateKey());
    }

    @Test
    public void resolve_WhenCertificateFileConfigured_ShouldLoadCertificate() throws IOException {
        var uri = getClass().getClassLoader().getResource(CERT_EXAMPLE);
        byte[] expected = readBytes(CERT_EXAMPLE);

        when(configuration.getCertFile()).thenReturn(uri.getPath());

        assertArrayEquals(expected, securityProvider.resolve(configuration).certificate());
    }

    @Test
    public void resolve_WhenCertificateUriConfigured_ShouldLoadCertificate() throws IOException {
        var uri = getClass().getClassLoader().getResource(CERT_EXAMPLE);

        when(configuration.getCertUri()).thenReturn(uri.toString());
        byte[] expected = readBytes(CERT_EXAMPLE);

        assertArrayEquals(expected, securityProvider.resolve(configuration).certificate());
    }

    @Test
    public void resolve_WhenCertificateBytesConfigured_ShouldUseCertificateBytes() throws IOException {
        byte[] expected = readBytes(CERT_EXAMPLE);
        when(configuration.getCertBytes()).thenReturn(expected);

        assertArrayEquals(expected, securityProvider.resolve(configuration).certificate());
    }

    @Test
    public void resolve_WhenKnownHostsConfigured_ShouldLoadKnownHosts() throws IOException {
        byte[] expected = readBytes("known_hosts");
        when(configuration.getKnownHostsUri()).thenReturn("known_hosts");
        byte[] actual = securityProvider.resolve(configuration).knownHostsIS().readAllBytes();
        assertArrayEquals(expected, actual);

    }

    @Test
    public void resolve_WhenPublicKeyAcceptedAlgorithmsConfigured_ShouldNotDetectCertKeyType() throws IOException {
        when(configuration.getPublicKeyAcceptedAlgorithms()).thenReturn("PublicKeyAcceptedAlgorithms");
        assertNull(securityProvider.resolve(configuration).certKeyType());
    }

    @Test
    public void resolve_WhenCertificateContainsLegacyKeyType_ShouldDetectKeyType() throws IOException {
        String certType = "ssh-rsa-cert";
        byte[] cert = (certType + " AAAA123").getBytes(StandardCharsets.UTF_8);
        when(configuration.getCertBytes()).thenReturn(cert);
        assertEquals(certType, securityProvider.resolve(configuration).certKeyType());
    }

    @Test
    public void resolve_WhenCertificateContainsOpenSshKeyType_ShouldDetectKeyType() throws IOException {
        String certType = "ssh-ed25519-cert-v01@openssh.com";
        byte[] cert = (certType + " AAAA123").getBytes(StandardCharsets.UTF_8);
        when(configuration.getCertBytes()).thenReturn(cert);
        assertEquals(certType, securityProvider.resolve(configuration).certKeyType());
    }

    private byte @NonNull [] readBytes(String resourceName) throws IOException {
        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream(resourceName)) {

            assertNotNull(input, () -> "Test resource not found: " + resourceName);
            return input.readAllBytes();
        }
    }
}