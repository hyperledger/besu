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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.prng.SP800SecureRandom;
import org.bouncycastle.crypto.prng.SP800SecureRandomBuilder;
import org.junit.Test;

public class PRNGSecureRandomTest {

  @Test
  public void createsSecureRandomInitialisedToUsePRNG() {
    final QuickEntropy quickEntropy = mock(QuickEntropy.class);
    final SP800SecureRandomBuilder sp800Builder = mock(SP800SecureRandomBuilder.class);

    when(sp800Builder.setSecurityStrength(anyInt())).thenReturn(sp800Builder);
    when(sp800Builder.setPersonalizationString(any())).thenReturn(sp800Builder);

    new PRNGSecureRandom(quickEntropy, sp800Builder);
    verify(sp800Builder).buildHash(any(SHA256Digest.class), eq(null), eq(false));
    verify(sp800Builder).setSecurityStrength(256);
    verify(sp800Builder).setPersonalizationString(any());
  }

  @Test
  public void reseedsUsingQuickEntropyOnEachNextByteCall() {
    final QuickEntropy quickEntropy = mock(QuickEntropy.class);
    final SP800SecureRandomBuilder sp800Builder = mock(SP800SecureRandomBuilder.class);
    final SP800SecureRandom sp800SecureRandom = mock(SP800SecureRandom.class);

    final byte[] entropy = {1, 2, 3, 4};
    when(quickEntropy.getQuickEntropy()).thenReturn(entropy);
    when(sp800Builder.setSecurityStrength(anyInt())).thenReturn(sp800Builder);
    when(sp800Builder.setPersonalizationString(any())).thenReturn(sp800Builder);
    when(sp800Builder.buildHash(any(), any(), anyBoolean())).thenReturn(sp800SecureRandom);

    final PRNGSecureRandom prngSecureRandom = new PRNGSecureRandom(quickEntropy, sp800Builder);
    final byte[] bytes = new byte[] {};
    prngSecureRandom.nextBytes(bytes);
    verify(quickEntropy, times(1)).getQuickEntropy();
    verify(sp800SecureRandom).setSeed(entropy);
    verify(sp800SecureRandom).nextBytes(bytes);
  }
}
