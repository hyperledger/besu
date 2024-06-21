/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.privacy;

import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY_PROXY;
import static org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement.ADD_PARTICIPANTS_METHOD_SIGNATURE;

import org.hyperledger.besu.datatypes.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class FlexibleUtil {

  private FlexibleUtil() {}

  public static boolean isGroupAdditionTransaction(final PrivateTransaction privateTransaction) {
    final Optional<Address> to = privateTransaction.getTo();
    return to.isPresent()
        && to.get().equals(FLEXIBLE_PRIVACY_PROXY)
        && privateTransaction
            .getPayload()
            .toHexString()
            .startsWith(ADD_PARTICIPANTS_METHOD_SIGNATURE.toHexString());
  }

  public static List<String> getParticipantsFromParameter(final Bytes input) {
    if (input.size() < 68) return new ArrayList<>();
    final int numberOfParticipants = UInt256.fromBytes(input.slice(4 + 32, 32)).toInt();

    // Method selector + offset +  number of participants + (offset * number of participants)
    final Bytes encodedParticipants = input.slice(4 + 32 + 32 + (32 * numberOfParticipants));

    return getParticipantsFromEncodedParticipants(encodedParticipants, numberOfParticipants);
  }

  public static List<String> decodeList(final Bytes rlpEncodedList) {
    if (rlpEncodedList.size() < 64) return new ArrayList<>();

    // Bytes uses a byte[] for the content which can only have up to Integer.MAX_VALUE-5 elements
    final int lengthOfList =
        UInt256.fromBytes(rlpEncodedList.slice(32, 32)).toInt(); // length of list

    final Bytes encodedParticipants = rlpEncodedList.slice(32 + 32 + (32 * lengthOfList));

    return getParticipantsFromEncodedParticipants(encodedParticipants, lengthOfList);
  }

  private static List<String> getParticipantsFromEncodedParticipants(
      final Bytes encodedParticipants, final int numberOfParticipants) {
    final List<String> participants = new ArrayList<>();

    if (numberOfParticipants == 0) return participants;
    // The participant value is enclosed in the closest multiple of 32 (for instance, 91 would be
    // enclosed in 96)
    final int sliceSize = encodedParticipants.size() / numberOfParticipants;

    // All the participants have to have the same size, so it is enough to check the first one
    final int participantSize = UInt256.fromBytes(encodedParticipants.slice(0, 32)).toInt();

    // Each slice should have a size of 32 bytes (because of the size value of each participant) +
    // the actual participant wrapped in a 32 byte long multiple (96 for 91)
    final int mod32ParticipantsSize = participantSize % 32;
    final int participantSizeBytes32Wrapped =
        mod32ParticipantsSize != 0
            ? (32 - mod32ParticipantsSize) + participantSize
            : participantSize;
    if (sliceSize != 32 + participantSizeBytes32Wrapped) return participants;

    for (int i = 0; i <= encodedParticipants.size() - sliceSize; i += sliceSize) {
      // The size of each participant (as of now, either 32 or 91 because of the enclave public key
      // size for NaCl and EC) is stored in 32 bytes
      participants.add(encodedParticipants.slice(i + 32, participantSize).toBase64String());
    }

    return participants;
  }
}
