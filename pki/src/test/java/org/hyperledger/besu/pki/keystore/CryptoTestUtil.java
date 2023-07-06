/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.pki.keystore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CryptoTestUtil {

  private static final Logger LOG = LoggerFactory.getLogger(CryptoTestUtil.class);

  private CryptoTestUtil() {}

  public static boolean isNSSLibInstalled() {
    try {
      final String nssLibPath = getNSSLibPath();
      return nssLibPath != null && !nssLibPath.trim().isEmpty();
    } catch (final Exception e) {
      LOG.info("NSS library does not seem to be installed!", e);
    }
    return false;
  }

  public static String getNSSLibPath() throws IOException, InterruptedException {
    String nssLibPath = "";
    final String centOS_nssPathCmd =
        "whereis libnssdbm3 | grep -o \"\\/.*libnssdbm3\\.[0-9a-z]* \" | sed 's/\\/libnssdbm3.*//g'";
    final String debian_nssPathCmd =
        "whereis libnss3 | grep  -o \".*libnss3.[0-9a-z]\" | sed 's/lib.* \\(\\/.*\\)\\/lib.*/\\1/'";
    final String macOS_nssPathCmd = "dirname `which certutil` | sed 's/bin/lib/g'";

    nssLibPath = executeSystemCmd(centOS_nssPathCmd).orElse(nssLibPath);
    LOG.info("centOS_nssPathCmd: {}", nssLibPath);
    if ("".equals(nssLibPath)) {
      nssLibPath = executeSystemCmd(debian_nssPathCmd).orElse(nssLibPath);
      LOG.info("debian_nssPathCmd: {}", nssLibPath);
    }
    if ("".equals(nssLibPath)) {
      nssLibPath = executeSystemCmd(macOS_nssPathCmd).orElse(nssLibPath);
      LOG.info("macOS_nssPathCmd: {}", nssLibPath);
    }
    LOG.info("Detected NSS library path: {}", nssLibPath);
    return nssLibPath;
  }

  public static Optional<String> executeSystemCmd(final String cmd)
      throws IOException, InterruptedException {
    final Process p = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", cmd});
    try {
      if (p.waitFor() == 0) {
        final java.util.Scanner s =
            new java.util.Scanner(p.getInputStream(), StandardCharsets.UTF_8.name())
                .useDelimiter("\\A");
        if (s.hasNext()) {
          return Optional.of(s.next().replace("\r", "").replace("\n", ""));
        }
      }
    } finally {
      if (p != null) {
        p.destroy();
      }
    }
    return Optional.empty();
  }
}
