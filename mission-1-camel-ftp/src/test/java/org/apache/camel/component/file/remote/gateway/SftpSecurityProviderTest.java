package org.apache.camel.component.file.remote.gateway;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.remote.SftpConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SftpSecurityProviderTest {
    SftpSecurityProvider securityProvider;
    @Mock
    SftpConfiguration configuration;
    @Mock
    CamelContext camelContext;
    @BeforeEach
    void setUp() {
        securityProvider = new SftpSecurityProvider(camelContext);
    }

    @Test
    void load_WhenConfigEmpty_ShouldReturnFalse() {
        assertNull(securityProvider.resolveCertificateBytes(configuration));
        assertNull(securityProvider.loadKnownHostsIS(null));
        assertNull(securityProvider.loadPrivateKey(null));
        assertNull(securityProvider.calculateCertKeyType(null,null));
    }


}