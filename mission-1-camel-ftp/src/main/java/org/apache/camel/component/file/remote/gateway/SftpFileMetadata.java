package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.ChannelSftp;

public record SftpFileMetadata(String filename, String longname, long length, long MTime, boolean dir) {

    static SftpFileMetadata fromLsEntry(ChannelSftp.LsEntry entry) {

        return new SftpFileMetadata(
                entry.getFilename(),
                entry.getLongname(),
                entry.getAttrs().getSize(),
                entry.getAttrs().getMTime(),
                entry.getAttrs().isDir()
        );
    }
}
