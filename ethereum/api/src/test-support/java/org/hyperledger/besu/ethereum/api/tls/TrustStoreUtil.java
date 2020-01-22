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
