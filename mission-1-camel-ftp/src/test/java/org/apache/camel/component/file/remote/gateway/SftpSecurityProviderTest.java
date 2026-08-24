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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    void resolveTest_WhenConfigEmpty(){
        securityProvider.resolve(configuration);
    }

    @Test
    void load_WhenConfigEmpty_ShouldReturnNull() {
        assertNull(securityProvider.resolve(configuration).certificate());
        assertNull(securityProvider.loadKnownHostsIS(null));
        assertNull(securityProvider.loadPrivateKey(null));
        assertNull(securityProvider.calculateCertKeyType(null, null));
    }

    @Test
    void load_WhenConfigIncorrectPath_ShouldThrowException() {
        assertThrows(SftpClientException.class, () -> securityProvider.loadKnownHostsIS(INCORRECT_PATH));
        assertThrows(SftpClientException.class, () -> securityProvider.loadPrivateKey(INCORRECT_PATH));

        when(configuration.getCertFile()).thenReturn(INCORRECT_PATH);
        Exception exception = assertThrows(SftpClientException.class, () -> securityProvider.resolve(configuration).certificate());
        assertEquals("Cannot read certificate file: "+INCORRECT_PATH,exception.getMessage());
    }

    @Test
    void load_WhenConfigIncorrectCertUri_ShouldThrowException() {
        when(configuration.getCertUri()).thenReturn(INCORRECT_PATH);
        Exception exception = assertThrows(SftpClientException.class, () -> securityProvider.resolve(configuration).certificate());
        assertEquals("Cannot read certificate resource: "+INCORRECT_PATH,exception.getMessage());

    }

    @Test
    public void loadPrivateKey_withPrivateKeyNotNull() throws IOException {
        byte[] expected = readBytes(PRIVATE_KEY_PATH);
        assertArrayEquals(expected, securityProvider.loadPrivateKey(PRIVATE_KEY_PATH));
    }

    @Test
    public void resolveCertificate_withCertificateFileNotNull() throws IOException {
        var uri = getClass().getClassLoader().getResource(CERT_EXAMPLE);
        byte[] expected = readBytes(CERT_EXAMPLE);

        when(configuration.getCertFile()).thenReturn(uri.getPath());

        assertArrayEquals(expected, securityProvider.resolve(configuration).certificate());
    }

    private byte @NonNull [] readBytes(String certExample) throws IOException {
        byte[] expected;
        try (InputStream input = getClass()
                .getClassLoader()
                .getResourceAsStream(certExample)) {

            expected = input.readAllBytes();
        }
        return expected;
    }

    @Test
    public void resolveCertificate_withCertificateUriNotNull() throws IOException {
        var uri = getClass().getClassLoader().getResource(CERT_EXAMPLE);

        when(configuration.getCertUri()).thenReturn(uri.toString());
        byte[] expected = readBytes(CERT_EXAMPLE);

        assertArrayEquals(expected, securityProvider.resolve(configuration).certificate());
    }

    @Test
    public void resolveCertificate_withCertBytesNotNull() throws IOException {
        byte[] expected = readBytes(CERT_EXAMPLE);
        when(configuration.getCertBytes()).thenReturn(expected);

        assertArrayEquals(expected, securityProvider.resolve(configuration).certificate());
    }

    @Test
    public void loadKnownHostsIS() throws IOException {
        byte[] expected = readBytes("known_hosts");
        byte[] actual = securityProvider.loadKnownHostsIS("known_hosts").readAllBytes();
        assertArrayEquals(expected, actual);

    }

    @Test
    public void calculateCertKeyType_withPublicKeyAcceptedAlgorithmsIsNotNull_ShouldReturnNull() throws IOException {
        byte[] certData = readBytes(CERT_EXAMPLE);
        assertNull(securityProvider.calculateCertKeyType("PublicKeyAcceptedAlgorithms", certData));
    }

    @Test
    public void calculateCertKeyType_withPublicKeyAcceptedAlgorithmsIsNull_ShouldReturnKeyTypeSshRsaCert() throws IOException {
        String certType = "ssh-rsa-cert";
        byte[] cert = (certType + " AAAA123").getBytes(StandardCharsets.UTF_8);
        assertEquals(certType, securityProvider.calculateCertKeyType(null, cert));
    }

    @Test
    public void calculateCertKeyType_withPublicKeyAcceptedAlgorithmsIsNull_ShouldReturnAlgorithmsKeyType() throws IOException {
        String certType = "ssh-ed25519-cert-v01@openssh.com";
        byte[] cert = (certType + " AAAA123").getBytes(StandardCharsets.UTF_8);
        assertEquals(certType, securityProvider.calculateCertKeyType(null, cert));
    }
}