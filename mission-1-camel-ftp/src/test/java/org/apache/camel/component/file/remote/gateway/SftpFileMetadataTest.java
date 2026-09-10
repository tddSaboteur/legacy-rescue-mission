package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SftpFileMetadataTest {

    @Mock
    ChannelSftp.LsEntry entry;
    @Mock
    SftpATTRS attrs;

    @Test
    void testMapping_empty_entry(){
        when(entry.getAttrs()).thenReturn(attrs);
        SftpFileMetadata.fromLsEntry(entry);
    }

    @Test
    void testMapping_configured_entry(){
        Boolean isDir = Boolean.FALSE;
        int mTime = 1;
        long size = 11L;
        String filename = "FILENAME";
        String longname = "LONG_NAME";

        when(entry.getAttrs()).thenReturn(attrs);
        when(entry.getFilename()).thenReturn(filename);
        when(entry.getLongname()).thenReturn(longname);
        when(attrs.isDir()).thenReturn(isDir);
        when(attrs.getMTime()).thenReturn(mTime);
        when(attrs.getSize()).thenReturn(size);

        SftpFileMetadata metadata = SftpFileMetadata.fromLsEntry(entry);

        assert metadata != null;
        assertEquals(metadata.dir(),isDir);
        assertEquals(metadata.MTime(), mTime);
        assertEquals(metadata.length(),size);
        assertEquals(metadata.filename(),filename);
        assertEquals(metadata.longname(),longname);


    }
}