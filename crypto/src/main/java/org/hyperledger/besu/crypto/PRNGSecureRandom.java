/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.crypto;

import java.security.SecureRandom;

import com.google.common.annotations.VisibleForTesting;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.prng.SP800SecureRandomBuilder;

public class PRNGSecureRandom extends SecureRandom {
  private static final int SECURITY_STRENGTH = 256;
  private final SecureRandom sp800SecureRandom;
  private final QuickEntropy quickEntropy;

  public PRNGSecureRandom() {
    this(new QuickEntropy(), new SP800SecureRandomBuilder());
  }

  @VisibleForTesting
  protected PRNGSecureRandom(
      final QuickEntropy quickEntropy, final SP800SecureRandomBuilder sp800SecureRandomBuilder) {
    final Digest digest = new SHA256Digest();
    final byte[] personalizationString = PersonalisationString.getPersonalizationString();
    this.quickEntropy = quickEntropy;
    // prediction resistance is not required as we are applying a light reseed on each nextBytes
    // with quick entropy.
    this.sp800SecureRandom =
        sp800SecureRandomBuilder
            .setSecurityStrength(SECURITY_STRENGTH)
            .setPersonalizationString(personalizationString)
            .buildHash(digest, null, false);
  }

  @Override
  public String getAlgorithm() {
    return sp800SecureRandom.getAlgorithm();
  }

  @Override
  /*
    JDK SecureRandom.setSeed method is synchronized on some JDKs, it varies between versions.
    But sync at method level isn't needed as we are delegating to SP800SecureRandom and it uses a sync block.
  */
  @SuppressWarnings("UnsynchronizedOverridesSynchronized")
  public void setSeed(final byte[] seed) {
    sp800SecureRandom.setSeed(seed);
  }

  @Override
  /*
    JDK SecureRandom.setSeed method is synchronized on some JDKs, it varies between versions.
    But sync at method level isn't needed as we are delegating to SP800SecureRandom and it uses a sync block.
  */
  @SuppressWarnings("UnsynchronizedOverridesSynchronized")
  public void setSeed(final long seed) {
    // As setSeed is called by the super constructor this can be called before the sp800SecureRandom
    // field is initialised
    if (sp800SecureRandom != null) {
      sp800SecureRandom.setSeed(seed);
    }
  }

  @Override
  /*
    JDK SecureRandom.nextBytes method is synchronized on some JDKs, it varies between versions.
    But sync at method level isn't needed as we are delegating to SP800SecureRandom and it uses a sync block.
  */
  @SuppressWarnings("UnsynchronizedOverridesSynchronized")
  public void nextBytes(final byte[] bytes) {
    sp800SecureRandom.setSeed(quickEntropy.getQuickEntropy());
    sp800SecureRandom.nextBytes(bytes);
  }

  @Override
  public byte[] generateSeed(final int numBytes) {
    sp800SecureRandom.setSeed(quickEntropy.getQuickEntropy());
    return sp800SecureRandom.generateSeed(numBytes);
  }
}
