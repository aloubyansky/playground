/*
 * Copyright 2020, Harsha Ramesh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.acme.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.scp.ScpCommandFactory;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MockSSHServer implements QuarkusTestResourceLifecycleManager, BeforeAllCallback, AfterAllCallback {

    private static final String CLASS_NAME = MockSSHServer.class.getName();
    private static final Logger LOGGER = Logger.getLogger(CLASS_NAME);
    private static final String SSH_USERNAME = "unit-test";
    private final SshServer sshd;

    public MockSSHServer() {
        String host = "127.0.0.1";
        int port = getFreePort();
        sshd = SshServer.setUpDefaultServer();
        sshd.setHost(host);
        sshd.setPort(port);

        sshd.setFileSystemFactory(new VirtualFileSystemFactory(createTempDir().toPath()));
        sshd.setShellFactory(new ProcessShellFactory("/bin/sh", "-i", "-l", "-r"));
        List<NamedFactory<Command>> subsystems = new ArrayList<>();
        List<NamedFactory<Command>> tmp = sshd.getSubsystemFactories();
        if (tmp != null) {
            subsystems.addAll(tmp);
        }
        subsystems.add(new SftpSubsystemFactory.Builder().build());
        sshd.setSubsystemFactories(subsystems);

        CommandFactory commandFactory = new ScpCommandFactory.Builder().withDelegate(new ProcessShellCommandFactory()).build();
        sshd.setCommandFactory(commandFactory);

        try {
            sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(File.createTempFile("unittest_sshd", ".ser").toPath()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Allow all authentication
        sshd.setPublickeyAuthenticator((username, key, session) -> {
            LOGGER.logp(Level.INFO, CLASS_NAME, "publicKeyAuthenticator",
                    "Returning true for public key authentication for user: {0}", username);
            return true;
        });
        sshd.setPasswordAuthenticator((username, password, session) -> {
            LOGGER.logp(Level.INFO, CLASS_NAME, "passwordAuthenticator",
                    "Returning true for password authentication for user: {0}", username);
            return true;
        });

    }

    @Override
    public Map<String, String> start() {
        final String METHOD_NAME = "start";
        try {
            LOGGER.logp(Level.INFO, CLASS_NAME, METHOD_NAME, "Starting the Mock SSH server.");
            sshd.start();
            Map<String, String> props = new HashMap<>();
            props.put("mock.sshd.host", sshd.getHost());
            props.put("mock.sshd.port", Integer.toString(sshd.getPort()));
            props.put("mock.ssh.username", SSH_USERNAME);
            return props;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void stop() {
        final String METHOD_NAME = "stop";
        try {
            LOGGER.logp(Level.INFO, CLASS_NAME, METHOD_NAME, "Stopping the Mock SSH server.");
            sshd.stop();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        start().forEach(System::setProperty);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        stop();
    }

    /**
     * Get a random port that is currently free.
     *
     * @return a random port that is currently free.
     */
    private static int getFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Convenience method for creating a temporary directory.
     *
     * @return the temporary directory.
     */
    private static File createTempDir() {
        File systemTmpDirRoot = new File(System.getProperty("java.io.tmpdir"));
        File tmpDir = null;
        int idx = 0;
        String name = String.format("%s-%s", SSH_USERNAME, System.currentTimeMillis());
        while (tmpDir == null) {
            File f = new File(systemTmpDirRoot, name);
            if (f.exists()) {
                // Potential race condition within the code.
                idx++;
                name = String.format("%s-%d-%s", SSH_USERNAME, idx, System.currentTimeMillis());
            } else {
                tmpDir = f;
            }
        }
        assertTrue(tmpDir.mkdirs(), "Unable to create tmp directory: " + tmpDir.getAbsolutePath());
        tmpDir.deleteOnExit();
        return tmpDir;
    }

}
