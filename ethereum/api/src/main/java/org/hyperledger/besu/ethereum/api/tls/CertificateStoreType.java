/*
 * Copyright (C) 2019 ConsenSys AG.
 *
 * The source code is provided to licensees of PegaSys Plus for their convenience and internal
 * business use. These files may not be copied, translated, and/or distributed without the express
 * written permission of an authorized signatory for ConsenSys AG.
 */
package org.hyperledger.besu.ethereum.api.tls;

import java.util.EnumSet;
import java.util.Optional;

public enum CertificateStoreType {
  JKS(true),
  PKCS12(true),
  PEM(false);

  private boolean passwordRequired;

  CertificateStoreType(final boolean passwordRequired) {
    this.passwordRequired = passwordRequired;
  }

  public boolean isPasswordRequired() {
    return passwordRequired;
  }

  public static Optional<CertificateStoreType> fromString(final String type) {
    return EnumSet.allOf(CertificateStoreType.class).stream()
        .filter(t -> t.name().equals(type))
        .findAny();
  }
}
