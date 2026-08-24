package org.apache.camel.component.file.remote.gateway;

public record SecurityMaterials(byte[] certificate, byte[] privateKey, java.io.InputStream knownHostsIS, String certKeyType) {
}
