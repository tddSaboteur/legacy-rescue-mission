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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Proxy;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.file.GenericFileHelper;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.exception.SftpClientException;
import org.apache.camel.component.file.remote.gateway.JschSetup;
import org.apache.camel.component.file.remote.gateway.JschSftpClient;
import org.apache.camel.component.file.remote.gateway.SftpClient;
import org.apache.camel.component.file.remote.gateway.SftpSecurityProvider;
import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * SFTP remote file operations
 * <p/>
 * The JSCH session and channel are not thread-safe so we need to synchronize access to using this operation.
 */
public class SftpOperations implements RemoteFileOperations<SftpRemoteFile> {
    private static final Logger LOG = LoggerFactory.getLogger(SftpOperations.class);
    private static final Pattern UP_DIR_PATTERN = Pattern.compile("/[^/]+");
    private static final int OK_STATUS = 0;
    private static final String OK_MESSAGE = "OK";
    private SftpEndpoint endpoint;

    private final Lock lock = new ReentrantLock();
    private SftpClient jschClient;
    private final Proxy proxy;

    private static class TaskPayload {
        final RemoteFileConfiguration configuration;
        private Exception exception;

        public TaskPayload(RemoteFileConfiguration configuration) {
            this.configuration = configuration;
        }
    }
    /**
     * @deprecated Используйте {@link #SftpOperations(SftpClient)} для явного
     * внедрения зависимостей. Этот конструктор оставлен только для обратной
     * совместимости и legacy-кода.
     */
    @Deprecated
    public SftpOperations() {
        this.proxy = null;
    }

    /**
     * @deprecated Используйте {@link #SftpOperations(SftpClient)} для явного
     * внедрения зависимостей. Этот конструктор оставлен только для обратной
     * совместимости и legacy-кода.
     */
    @Deprecated
    public SftpOperations(Proxy proxy) {
        this.proxy = proxy;
    }

    public SftpOperations(SftpClient jschClient) {
        this.jschClient = jschClient;
        this.proxy = null;
    }

    /**
     * Extended user info which supports interactive keyboard mode, by entering the password.
     */

    @Override
    public void setEndpoint(GenericFileEndpoint<SftpRemoteFile> endpoint) {
        this.endpoint = (SftpEndpoint) endpoint;
        createSftpClient();
    }

    private void createSftpClient(){
        if (jschClient == null){
            this.jschClient = new JschSftpClient(new SftpSecurityProvider(endpoint.getCamelContext()),proxy);
        }
    }

    @Override
    public GenericFile<SftpRemoteFile> newGenericFile() {
        return new RemoteFile<>();
    }

    @Override
    public boolean connect(RemoteFileConfiguration configuration, Exchange exchange)
            throws GenericFileOperationFailedException {
        lock.lock();
        try {
            if (isConnected()) {
                // already connected
                return true;
            }

            BlockingTask task = Tasks.foregroundTask()
                    .withBudget(Budgets.iterationBudget()
                            .withMaxIterations(Budgets.atLeastOnce(endpoint.getMaximumReconnectAttempts()))
                            .withInterval(Duration.ofMillis(endpoint.getReconnectDelay()))
                            .build())
                    .build();

            TaskPayload payload = new TaskPayload(configuration);

            if (!task.run(endpoint.getCamelContext(), this::tryConnect, payload)) {
                throw new GenericFileOperationFailedException(
                        "Cannot connect to " + configuration.remoteServerInformation(),
                        payload.exception);
            }

            return true;
        } finally {
            lock.unlock();
        }
    }

    private boolean tryConnect(TaskPayload payload) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Reconnect attempt to {}", payload.configuration.remoteServerInformation());
        }
        try {
            if (!jschClient.isConnected()) {
                initialiseJsch(payload.configuration);
            }
        } catch (SftpClientException e) {
            payload.exception = e;

            return false;
        }

        return true;
    }

    protected void initialiseJsch(final RemoteFileConfiguration configuration) throws SftpClientException {

        SftpConfiguration sftpConfig = (SftpConfiguration) configuration;




        jschClient.init(new JschSetup(sftpConfig));
    }

    @Override
    public boolean isConnected() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            return jschClient.isConnected();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void disconnect() throws GenericFileOperationFailedException {
        lock.lock();

        try {
            jschClient.disconnectSftp();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void forceDisconnect() throws GenericFileOperationFailedException {
        lock.lock();
        jschClient.forceDisconnect();
        lock.unlock();
    }

    private void reconnectIfNecessary(Exchange exchange) {
        if (!isConnected()) {
            connect(endpoint.getConfiguration(), exchange);
        }
    }

    @Override
    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.debug("Deleting file: {}", name);
            reconnectIfNecessary(null);
            jschClient.rm(name);
            return true;
        } catch (SftpClientException e) {
            LOG.debug("Cannot delete file {}: {}", name, e.getMessage(), e);
            throw new GenericFileOperationFailedException("Cannot delete file: " + name, e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        lock.lock();

        try {
            LOG.debug("Renaming file: {} to: {}", from, to);
            reconnectIfNecessary(null);
            // make use of the '/' separator because JSch expects this
            // as the file separator even on Windows
            to = FileUtil.compactPath(to, '/');
            jschClient.channelRename(from, to);
            return true;
        } catch (SftpClientException e) {
            LOG.debug("Cannot rename file from: {} to: {}", from, to, e);
            throw new GenericFileOperationFailedException("Cannot rename file from: " + from + " to: " + to, e);
        } finally {
            lock.unlock();
        }
    }


    @Override
    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            // must normalize directory first
            directory = endpoint.getConfiguration().normalizePath(directory);

            LOG.trace("buildDirectory({},{})", directory, absolute);
            // ignore absolute as all dirs are relative with FTP
            boolean success = false;

            // whether to check for existing dir using CD or LS
            boolean cdCheck = !this.endpoint.getConfiguration().isExistDirCheckUsingLs();
            String originalDirectory = cdCheck ? getCurrentDirectory() : null;

            try {
                // maybe the full directory already exists
                try {
                    if (cdCheck) {
                        jschClient.cd(directory);
                    } else {
                        // just do a fast listing
                        jschClient.lsByBreakSelector(directory);
                    }
                    success = true;
                } catch (SftpClientException e) {
                    // ignore, we could not change directory so try to create it
                    // instead
                }

                if (!success) {
                    LOG.debug("Trying to build remote directory: {}", directory);
                    try {
                        jschClient.mkdir(directory);
                        success = true;
                    } catch (SftpClientException e) {
                        // we are here if the server side doesn't create
                        // intermediate folders
                        // so create the folder one by one
                        success = buildDirectoryChunks(directory);
                    }

                    // only after successfully creating directory, we may set chmod on the file
                    if (success) {
                        chmodOfDirectory(directory);
                    }
                }

                // change back to original directory
                if (originalDirectory != null) {
                    changeCurrentDirectory(originalDirectory);
                }
            } catch (SftpClientException e) {
                throw new GenericFileOperationFailedException("Cannot build directory: " + directory, e);
            }

            return success;
        } finally {
            lock.unlock();
        }
    }


    private boolean buildDirectoryChunks(String dirName) throws SftpClientException {
        final StringBuilder sb = new StringBuilder(dirName.length());
        final String[] dirs = dirName.split("/|\\\\");
        boolean success = false;
        boolean first = true;
        for (String dir : dirs) {
            if (first) {
                first = false;
            } else {
                sb.append('/');
            }
            sb.append(dir);

            // must normalize the directory name
            String directory = endpoint.getConfiguration().normalizePath(sb.toString());

            // do not try to build root folder (/ or \)
            if (!(directory.equals("/") || directory.equals("\\"))) {
                try {
                    LOG.trace("Trying to build remote directory by chunk: {}", directory);

                    jschClient.mkdir(directory);
                    success = true;
                } catch (SftpClientException e) {
                    // ignore keep trying to create the rest of the path
                }

                // only after successfully creating directory, we may set chmod on the file
                if (success) {
                    chmodOfDirectory(directory);
                }
            }
        }

        return success;
    }


    @Override
    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("getCurrentDirectory()");
            String answer = jschClient.pwd();
            LOG.trace("Current dir: {}", answer);
            return answer;
        } catch (SftpClientException e) {
            throw new GenericFileOperationFailedException("Cannot get current directory", e);
        } finally {
            lock.unlock();
        }
    }


    @Override
    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("changeCurrentDirectory({})", path);
            if (ObjectHelper.isEmpty(path)) {
                return;
            }

            // must compact path so SFTP server can traverse correctly, make use of
            // the '/'
            // separator because JSch expects this as the file separator even on
            // Windows
            String before = path;
            char separatorChar = '/';
            path = FileUtil.compactPath(path, separatorChar);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Compacted path: {} -> {} using separator: {}", before, path, separatorChar);
            }

            // not stepwise should change directory in one operation
            if (!endpoint.getConfiguration().isStepwise()) {
                doChangeDirectory(path);
                return;
            }
            if (getCurrentDirectory().startsWith(path)) {
                // extract the path segment relative to the target path and make
                // sure it keeps the preceding '/' for the regex op
                String p = getCurrentDirectory().substring(path.length() - (path.endsWith("/") ? 1 : 0));
                if (p.isEmpty()) {
                    return;
                }
                // the first character must be '/' and hence removed
                path = UP_DIR_PATTERN.matcher(p).replaceAll("/..").substring(1);
            }

            // if it starts with the root path then a little special handling for
            // that
            if (FileUtil.hasLeadingSeparator(path)) {
                // change to root path
                if (!path.matches("^[a-zA-Z]:(//|\\\\).*$")) {
                    doChangeDirectory(path.substring(0, 1));
                    path = path.substring(1);
                } else {
                    if (path.matches("^[a-zA-Z]:(//).*$")) {
                        doChangeDirectory(path.substring(0, 3));
                        path = path.substring(3);
                    } else if (path.matches("^[a-zA-Z]:(\\\\).*$")) {
                        doChangeDirectory(path.substring(0, 4));
                        path = path.substring(4);
                    }
                }
            }

            // split into multiple dirs
            final String[] dirs = path.split("/|\\\\");

            if (dirs == null || dirs.length == 0) {
                // path was just a relative single path
                doChangeDirectory(path);
                return;
            }

            // there are multiple dirs so do this in chunks
            for (String dir : dirs) {
                doChangeDirectory(dir);
            }
        } finally {
            lock.unlock();
        }
    }

    private void doChangeDirectory(String path) {
        if (path == null || ".".equals(path) || ObjectHelper.isEmpty(path)) {
            return;
        }
        LOG.trace("Changing directory: {}", path);
        try {
            jschClient.cd(path);
        } catch (SftpClientException e) {
            throw new GenericFileOperationFailedException("Cannot change directory to: " + path, e);
        }
    }

    @Override
    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("changeToParentDirectory()");
            String current = getCurrentDirectory();

            String parent = FileUtil.compactPath(current + "/..");
            // must start with absolute
            if (!parent.startsWith("/")) {
                parent = "/" + parent;
            }

            changeCurrentDirectory(parent);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SftpRemoteFile[] listFiles() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            return listFiles(".");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SftpRemoteFile[] listFiles(String path) throws GenericFileOperationFailedException {
        lock.lock();

        try {
            LOG.trace("Listing remote files from path {}", path);
            if (ObjectHelper.isEmpty(path)) {
                // list current directory if file path is not given
                path = ".";
            }

            Vector<?> files = jschClient.ls(path);

            return files.stream()
                    .map(f -> new SftpRemoteFileJCraft((ChannelSftp.LsEntry) f))
                    .toArray(SftpRemoteFileJCraft[]::new);
        } catch (SftpClientException e) {
            throw new GenericFileOperationFailedException("Cannot list directory: " + path, e);
        } finally {
            lock.unlock();
        }
    }


    @Override
    public boolean retrieveFile(String name, Exchange exchange, long size)
            throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("retrieveFile({})", name);
            if (ObjectHelper.isNotEmpty(endpoint.getLocalWorkDirectory())) {
                // local work directory is configured so we should store file
                // content as files in this local directory
                return retrieveFileToFileInLocalWorkDirectory(name, exchange);
            } else {
                // store file content directory as stream on the body
                return retrieveFileToStreamInBody(name, exchange);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            InputStream is = exchange.getIn().getHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM, InputStream.class);

            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new GenericFileOperationFailedException(e.getMessage(), e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToStreamInBody(String name, Exchange exchange) throws GenericFileOperationFailedException {
        try {
            String currentDir = null;
            GenericFile<ChannelSftp.LsEntry> target
                    = (GenericFile<ChannelSftp.LsEntry>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            ObjectHelper.notNull(target, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");

            String remoteName = name;
            if (endpoint.getConfiguration().isStepwise()) {
                // remember current directory
                currentDir = getCurrentDirectory();

                // change directory to path where the file is to be retrieved
                // (must do this as some FTP servers cannot retrieve using
                // absolute path)
                String path = FileUtil.onlyPath(name);
                if (path != null) {
                    changeCurrentDirectory(path);
                }
                // remote name is now only the file name as we just changed
                // directory
                remoteName = FileUtil.stripPath(name);
            }

            // use input stream which works with Apache SSHD used for testing
            InputStream is = jschClient.get(remoteName);

            if (endpoint.getConfiguration().isStreamDownload()) {
                target.setBody(is);
                exchange.getIn().setHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM, is);
            } else {
                // read the entire file into memory in the byte array
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOHelper.copyAndCloseInput(is, bos);
                // close the stream after done
                IOHelper.close(bos);

                target.setBody(bos.toByteArray());
            }

            createResultHeadersFromExchange(null, exchange);

            // change back to current directory if we changed directory
            if (currentDir != null) {
                changeCurrentDirectory(currentDir);
            }
            return true;
        } catch (SftpClientException e) {
            createResultHeadersFromExchange(e, exchange);
            throw new GenericFileOperationFailedException("Cannot retrieve file: " + name, e);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot retrieve file: " + name, e);
        }
    }


    @SuppressWarnings("unchecked")
    private boolean retrieveFileToFileInLocalWorkDirectory(String name, Exchange exchange)
            throws GenericFileOperationFailedException {
        File temp;
        File local = new File(endpoint.getLocalWorkDirectory());
        OutputStream os;
        GenericFile<ChannelSftp.LsEntry> file
                = (GenericFile<ChannelSftp.LsEntry>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
        ObjectHelper.notNull(file, "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");
        try {
            // use relative filename in local work directory
            String relativeName = file.getRelativeFilePath();

            File localWorkDir = local;
            temp = new File(local, relativeName + ".inprogress");
            local = new File(local, relativeName);

            // ensure the local work file stays within the local work directory (CAMEL-23765)
            if (endpoint.isJailStartingDirectory()) {
                GenericFileHelper.jailToLocalWorkDirectory(temp, localWorkDir);
                GenericFileHelper.jailToLocalWorkDirectory(local, localWorkDir);
            }

            // create directory to local work file
            local.mkdirs();

            // delete any existing files
            if (temp.exists()) {
                if (!FileUtil.deleteFile(temp)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + temp);
                }
            }
            if (local.exists()) {
                if (!FileUtil.deleteFile(local)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + local);
                }
            }

            // create new temp local work file
            if (!temp.createNewFile()) {
                throw new GenericFileOperationFailedException("Cannot create new local work file: " + temp);
            }

            // store content as a file in the local work directory in the temp
            // handle
            os = new FileOutputStream(temp);

            // set header with the path to the local work file
            exchange.getIn().setHeader(FtpConstants.FILE_LOCAL_WORK_PATH, local.getPath());
        } catch (Exception e) {
            throw new GenericFileOperationFailedException("Cannot create new local work file: " + local, e);
        }
        try {
            String currentDir = null;
            // store the java.io.File handle as the body
            file.setBody(local);

            String remoteName = name;
            if (endpoint.getConfiguration().isStepwise()) {
                // remember current directory
                currentDir = getCurrentDirectory();

                // change directory to path where the file is to be retrieved
                // (must do this as some FTP servers cannot retrieve using
                // absolute path)
                String path = FileUtil.onlyPath(name);
                if (path != null) {
                    changeCurrentDirectory(path);
                }
                // remote name is now only the file name as we just changed
                // directory
                remoteName = FileUtil.stripPath(name);
            }

            jschClient.get(remoteName, os);

            // change back to current directory if we changed directory
            if (currentDir != null) {
                changeCurrentDirectory(currentDir);
            }

        } catch (SftpClientException e) {
            createResultHeadersFromExchange(e, exchange);
            LOG.trace("Error occurred during retrieving file: {} to local directory. Deleting local work file: {}", name, temp);
            // failed to retrieve the file so we need to close streams and
            // delete in progress file
            // must close stream before deleting file
            IOHelper.close(os, "retrieve: " + name, LOG);
            boolean deleted = FileUtil.deleteFile(temp);
            if (!deleted) {
                LOG.warn("Error occurred during retrieving file: {} to local directory. Cannot delete local work file: {}",
                        name, temp);
            }
            throw new GenericFileOperationFailedException("Cannot retrieve file: " + name, e);
        } finally {
            IOHelper.close(os, "retrieve: " + name, LOG);
        }

        createResultHeadersFromExchange(null, exchange);
        LOG.debug("Retrieve file to local work file result: true");

        // operation went okay so rename temp to local after we have retrieved
        // the data
        LOG.trace("Renaming local in progress file from: {} to: {}", temp, local);
        try {
            if (!FileUtil.renameFile(temp, local, false)) {
                throw new GenericFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local);
            }
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot rename local work file from: " + temp + " to: " + local, e);
        }

        return true;
    }


    @Override
    public boolean storeFile(String name, Exchange exchange, long size)
            throws GenericFileOperationFailedException {
        lock.lock();
        try {
            // must normalize name first
            name = endpoint.getConfiguration().normalizePath(name);

            LOG.trace("storeFile({})", name);

            boolean answer;
            String currentDir = null;
            String path = FileUtil.onlyPath(name);
            String targetName = name;

            if (path != null && endpoint.getConfiguration().isStepwise()) {
                // must remember current dir so we stay in that directory after
                // the write
                currentDir = getCurrentDirectory();

                // change to path of name
                changeCurrentDirectory(path);

                // the target name should be without path, as we have changed
                // directory
                targetName = FileUtil.stripPath(name);
            }

            // store the file
            answer = doStoreFile(name, targetName, exchange);

            // change back to current directory if we changed directory
            if (currentDir != null) {
                changeCurrentDirectory(currentDir);
            }

            return answer;
        } finally {
            lock.unlock();
        }
    }

    private boolean doStoreFile(String name, String targetName, Exchange exchange) throws GenericFileOperationFailedException {
        LOG.trace("doStoreFile({})", targetName);

        // if an existing file already exists what should we do?
        if (endpoint.getFileExist() == GenericFileExist.Ignore || endpoint.getFileExist() == GenericFileExist.Fail
                || endpoint.getFileExist() == GenericFileExist.Move) {
            boolean existFile = existsFile(targetName);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Ignore) {
                // ignore but indicate that the file was written
                LOG.trace("An existing file already exists: {}. Ignore and do not override it.", name);
                return true;
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Fail) {
                throw new GenericFileOperationFailedException("File already exist: " + name + ". Cannot write new file.");
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Move) {
                // move any existing file first
                this.endpoint.getMoveExistingFileStrategy().moveExistingFile(endpoint, this, targetName);
            }
        }

        InputStream is = null;
        if (exchange.getIn().getBody() == null) {
            // Do an explicit test for a null body and decide what to do
            if (endpoint.isAllowNullBody()) {
                LOG.trace("Writing empty file.");
                is = new ByteArrayInputStream(new byte[]{});
            } else {
                throw new GenericFileOperationFailedException("Cannot write null body to file: " + name);
            }
        }

        try {
            if (is == null) {
                String charset = endpoint.getCharset();
                if (charset != null) {
                    // charset configured so we must convert to the desired
                    // charset so we can write with encoding
                    is = new ByteArrayInputStream(exchange.getIn().getMandatoryBody(String.class).getBytes(charset));
                    LOG.trace("Using InputStream {} with charset {}.", is, charset);
                } else {
                    is = exchange.getIn().getMandatoryBody(InputStream.class);
                }
            }

            final StopWatch watch = new StopWatch();
            LOG.debug("About to store file: {} using stream: {}", targetName, is);
            if (endpoint.getFileExist() == GenericFileExist.Append) {
                LOG.trace("Client appendFile: {}", targetName);
                jschClient.putModeAppend(targetName, is);
            } else {
                LOG.trace("Client storeFile: {}", targetName);
                // override is default
                jschClient.put(targetName, is);
            }
            if (LOG.isDebugEnabled()) {
                long time = watch.taken();
                LOG.debug("Took {} ({} millis) to store file: {} and FTP client returned: true",
                        TimeUtils.printDuration(time, true), time, targetName);
            }

            // after storing file, we may set chmod on the file
            String mode = endpoint.getConfiguration().getChmod();
            if (ObjectHelper.isNotEmpty(mode)) {
                // parse to int using 8bit mode
                int permissions = Integer.parseInt(mode, 8);
                LOG.trace("Setting chmod: {} on file: {}", mode, targetName);
                jschClient.chmod(targetName, permissions);
            }

            createResultHeadersFromExchange(null, exchange);
            return true;

        } catch (SftpClientException e) {
            createResultHeadersFromExchange(e, exchange);
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } catch (UnsupportedEncodingException | InvalidPayloadException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } finally {
            IOHelper.close(is, "store: " + name, LOG);
        }
    }


    @Override
    public boolean storeFileDirectly(String name, String payload) throws GenericFileOperationFailedException {
        lock.lock();
        ByteArrayInputStream bis = new ByteArrayInputStream(payload.getBytes());
        try {
            jschClient.put(name, bis);
            return true;
        } catch (SftpClientException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + name, e);
        } finally {
            IOHelper.close(bis);
            lock.unlock();
        }
    }

    @Override
    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            LOG.trace("existsFile({})", name);
            if (endpoint.isFastExistsCheck()) {
                return fastExistsFile(name);
            }
            // check whether a file already exists
            String directory = FileUtil.onlyPath(name);
            if (directory == null) {
                // assume current dir if no path could be extracted
                directory = ".";
            }
            String onlyName = FileUtil.stripPath(name);

            try {
                @SuppressWarnings("rawtypes")
                List files = jschClient.ls(directory);
                // can return either null or an empty list depending on FTP servers
                if (files == null) {
                    return false;
                }
                for (Object file : files) {
                    ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) file;
                    String existing = entry.getFilename();
                    LOG.trace("Existing file: {}, target file: {}", existing, name);
                    existing = FileUtil.stripPath(existing);
                    if (existing != null && existing.equals(onlyName)) {
                        return true;
                    }
                }
                return false;
            } catch (SftpClientException e) {

                throw new GenericFileOperationFailedException(e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    protected boolean fastExistsFile(String name) throws GenericFileOperationFailedException {
        LOG.trace("fastExistsFile({})", name);
        try {
            @SuppressWarnings("rawtypes")
            List files = jschClient.ls(name);
            if (files == null) {
                return false;
            }
            return !files.isEmpty();
        } catch (SftpClientException e) {
            throw new GenericFileOperationFailedException(e.getMessage(), e);
        }
    }

    @Override
    public boolean sendNoop() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            if (isConnected()) {
                return jschClient.sendKeepAliveMsg();
            }
            return false;
        } finally {
            lock.unlock();
        }
    }


    @Override
    public boolean sendSiteCommand(String command) throws GenericFileOperationFailedException {
        // is not implemented
        return true;
    }


    /**
     * Helper method which gets result code and message from sftpException and puts it into header. In case that
     * exception is null, it sets successfully response.
     */
    private void createResultHeadersFromExchange(SftpClientException sftpException, Exchange exchange) {
        // if exception is null, it means that result was ok
        if (sftpException == null) {
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, OK_STATUS);
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, OK_MESSAGE);
        } else {
            // store client reply information after the operation
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, sftpException.getStatusCode());
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, sftpException.getMessage());
        }
    }

    /**
     * Helper method which sets the path permissions
     */
    private void chmodOfDirectory(String directory) {
        String chmodDirectory = endpoint.getConfiguration().getChmodDirectory();
        if (ObjectHelper.isNotEmpty(chmodDirectory)) {
            LOG.trace("Setting permission: {} on directory: {}", chmodDirectory, directory);
            // parse to int using 8bit mode
            int permissions = Integer.parseInt(chmodDirectory, 8);
            try {
                jschClient.chmod(directory, permissions);
            } catch (SftpClientException e) {
                throw new GenericFileOperationFailedException("Cannot set permission on directory: " + directory, e);
            }
        }
    }
}