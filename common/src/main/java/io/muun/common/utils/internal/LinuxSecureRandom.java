package io.muun.common.utils.internal;

/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.Provider;
import java.security.SecureRandomSpi;


/**
 * A SecureRandom implementation that overrides the one provided by the JVM, and serves random
 * numbers by reading /dev/urandom.
 */
@SuppressWarnings("WeakerAccess")
public class LinuxSecureRandom extends SecureRandomSpi {

    private static final long serialVersionUID = 558617268669943306L;
    private static final LinuxSecureRandomProvider PROVIDER = new LinuxSecureRandomProvider();
    private static final FileInputStream URANDOM;

    static {
        try {
            URANDOM = new FileInputStream(new File("/dev/urandom"));
            if (URANDOM.read() == -1) {
                throw new IOException();
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException("/dev/urandom doesn't appear to exist or isn't openable", e);
        } catch (IOException e) {
            throw new RuntimeException("/dev/urandom doesn't appear to be readable", e);
        }
    }

    public static Provider getProvider() {
        return PROVIDER;
    }

    // DataInputStream is not thread safe, so each random object has its own.
    private transient DataInputStream inputStream;

    public LinuxSecureRandom() {
    }

    private synchronized DataInputStream getInputStream() {

        // Make inputStream lazy in order to work after deserialization.
        if (inputStream == null) {
            inputStream = new DataInputStream(URANDOM);
        }

        return inputStream;
    }

    @Override
    protected void engineSetSeed(byte[] bytes) {
    }

    @Override
    protected void engineNextBytes(byte[] bytes) {

        try {
            // This will block until all the bytes can be read.
            getInputStream().readFully(bytes);
        } catch (IOException e) {
            // Fatal error. Do not attempt to recover from this.
            throw new RuntimeException(e);
        }
    }

    @Override
    protected byte[] engineGenerateSeed(int numBytes) {

        final byte[] bytes = new byte[numBytes];
        engineNextBytes(bytes);
        return bytes;
    }

    private static class LinuxSecureRandomProvider extends Provider {

        private static final long serialVersionUID = -3594323785466131390L;

        LinuxSecureRandomProvider() {
            super("LinuxSecureRandom", 1.0, "Linux random number provider that uses /dev/urandom");
            put("SecureRandom.LinuxSecureRandom", LinuxSecureRandom.class.getName());
        }
    }
}