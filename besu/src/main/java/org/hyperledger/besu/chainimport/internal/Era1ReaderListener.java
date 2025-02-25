/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.chainimport.internal;

import org.hyperledger.besu.util.e2.E2BeaconState;
import org.hyperledger.besu.util.e2.E2SignedBeaconBlock;
import org.hyperledger.besu.util.e2.E2SlotIndex;
import org.hyperledger.besu.util.e2.E2StoreReaderListener;

public abstract class Era1ReaderListener implements E2StoreReaderListener {
  @Override
  public void handleBeaconState(final E2BeaconState beaconState) {
    throw new UnsupportedOperationException("Unable to handle E2BeaconState");
  }

  @Override
  public void handleSlotIndex(final E2SlotIndex slotIndex) {
    throw new UnsupportedOperationException("Unable to handle E2SlotIndex");
  }

  @Override
  public void handleSignedBeaconBlock(final E2SignedBeaconBlock signedBeaconBlock) {
    throw new UnsupportedOperationException("Unable to handle E2SignedBeaconBlock");
  }
}
