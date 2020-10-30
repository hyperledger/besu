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

package org.hyperledger.besu.enclave;

import org.hyperledger.besu.crypto.MessageDigestFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import com.google.common.collect.Lists;

public class TlsHelpers {

  private static KeyStore loadP12KeyStore(final File pkcsFile, final String password)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException {
    final KeyStore store = KeyStore.getInstance("pkcs12");
    try (final InputStream keystoreStream = new FileInputStream(pkcsFile)) {
      store.load(keystoreStream, password.toCharArray());
    } catch (IOException e) {
      throw new RuntimeException("Unable to load keystore.", e);
    }
    return store;
  }

  public static void populateFingerprintFile(
      final Path knownClientsPath,
      final TlsCertificateDefinition certDef,
      final Optional<Integer> serverPortToAppendToHostname)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

    final List<X509Certificate> certs = getCertsFromPkcs12(certDef);
    final StringBuilder fingerprintsToAdd = new StringBuilder();
    final String portFragment = serverPortToAppendToHostname.map(port -> ":" + port).orElse("");
    for (final X509Certificate cert : certs) {
      final String fingerprint = generateFingerprint(cert);
      fingerprintsToAdd.append(String.format("localhost%s %s%n", portFragment, fingerprint));
      fingerprintsToAdd.append(String.format("127.0.0.1%s %s%n", portFragment, fingerprint));
    }
    Files.writeString(knownClientsPath, fingerprintsToAdd.toString());
  }

  @SuppressWarnings("JdkObsolete") // java.util.Enumeration is baked into the Keystore API
  public static List<X509Certificate> getCertsFromPkcs12(final TlsCertificateDefinition certDef)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException {
    final List<X509Certificate> results = Lists.newArrayList();

    final KeyStore p12 = loadP12KeyStore(certDef.getPkcs12File(), certDef.getPassword());
    final Enumeration<String> aliases = p12.aliases();
    while (aliases.hasMoreElements()) {
      results.add((X509Certificate) p12.getCertificate(aliases.nextElement()));
    }
    return results;
  }

  private static String generateFingerprint(final X509Certificate cert)
      throws NoSuchAlgorithmException, CertificateEncodingException {
    final MessageDigest md = MessageDigestFactory.create("SHA-256");
    md.update(cert.getEncoded());
    final byte[] digest = md.digest();

    final StringJoiner joiner = new StringJoiner(":");
    for (final byte b : digest) {
      joiner.add(String.format("%02X", b));
    }

    return joiner.toString().toLowerCase();
  }
}
