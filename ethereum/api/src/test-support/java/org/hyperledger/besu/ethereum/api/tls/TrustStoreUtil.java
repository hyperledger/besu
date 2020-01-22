/*
 *
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
 *
 */
package org.hyperledger.besu.ethereum.api.tls;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

import org.apache.tuweni.net.tls.TLS;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509CertificateHolder;

public class TrustStoreUtil {
  public static String commonNameAndFingerPrint(
      final Path trustStore, final char[] password, final String alias) throws Exception {
    final KeyStore keyStore = KeyStore.getInstance(trustStore.toFile(), password);
    final Certificate certificate = keyStore.getCertificate(alias);
    final String hexFingerprint = TLS.certificateHexFingerprint(certificate);
    final String commonName = getCommonName(certificate);
    return String.format("%s %s", commonName, hexFingerprint);
  }

  private static String getCommonName(final Certificate certificate)
      throws IOException, CertificateEncodingException {
    final X500Name subject = new X509CertificateHolder(certificate.getEncoded()).getSubject();
    final RDN commonNameRdn = subject.getRDNs(BCStyle.CN)[0];
    return IETFUtils.valueToString(commonNameRdn.getFirst().getValue());
  }
}
