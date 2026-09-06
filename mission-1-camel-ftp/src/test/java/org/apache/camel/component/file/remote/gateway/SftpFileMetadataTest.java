package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.ChannelSftp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SftpFileMetadataTest {

    @Mock
    ChannelSftp.LsEntry entry;

    @Test
    void testMapping(){
        SftpFileMetadata.fromLsEntry(entry);
    }
}