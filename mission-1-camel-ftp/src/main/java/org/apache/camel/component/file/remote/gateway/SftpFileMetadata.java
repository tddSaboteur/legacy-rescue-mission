package org.apache.camel.component.file.remote.gateway;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;

public record SftpFileMetadata(String filename,String longname,SftpATTRS attrs) {

    static void fromLsEntry(ChannelSftp.LsEntry entry) {

    }
}
