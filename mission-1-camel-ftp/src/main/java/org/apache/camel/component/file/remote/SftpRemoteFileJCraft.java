/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.remote;

import com.jcraft.jsch.ChannelSftp;
import org.apache.camel.component.file.remote.gateway.SftpFileMetadata;

public class SftpRemoteFileJCraft implements SftpRemoteFile<SftpFileMetadata> {

    private final SftpFileMetadata metadata;

    public SftpRemoteFileJCraft(SftpFileMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public SftpFileMetadata getRemoteFile() {
        return metadata;
    }

    @Override
    public String getFilename() {
        return metadata.filename();
    }

    @Override
    public String getLongname() {
        return metadata.longname();
    }

    @Override
    public boolean isDirectory() {
        return metadata.dir();
    }

    @Override
    public long getFileLength() {
        return metadata.length();
    }

    @Override
    public long getLastModified() {
        return metadata.MTime() * 1000L;
    }
}
