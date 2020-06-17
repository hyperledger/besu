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
package org.hyperledger.besu.ethereum.p2p.rlpx.wire;

import static java.util.Comparator.comparing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Range;
import org.apache.tuweni.bytes.Bytes;

public class CapabilityMultiplexer {

  static final int WIRE_PROTOCOL_MESSAGE_SPACE = 16;
  private static final Comparator<Capability> CAPABILITY_COMPARATOR =
      comparing(Capability::getName).thenComparing(comparing(Capability::getVersion).reversed());

  private final ImmutableRangeMap<Integer, Capability> agreedCaps;
  private final ImmutableMap<Capability, Integer> capabilityOffsets;
  private final Map<String, SubProtocol> subProtocols = new HashMap<>();

  public CapabilityMultiplexer(
      final List<SubProtocol> subProtocols, final List<Capability> a, final List<Capability> b) {
    for (final SubProtocol subProtocol : subProtocols) {
      this.subProtocols.put(subProtocol.getName(), subProtocol);
    }
    agreedCaps = calculateAgreedCapabilities(a, b);
    capabilityOffsets = calculateCapabilityOffsets(agreedCaps);
  }

  public Set<Capability> getAgreedCapabilities() {
    return capabilityOffsets.keySet();
  }

  public SubProtocol subProtocol(final Capability cap) {
    return subProtocols.get(cap.getName());
  }

  /**
   * Prepares a message to send by offsetting its code based on the agreed capabilities.
   *
   * @param cap The capability (protocol) associated with this message.
   * @param messageToSend The message to send.
   * @return Returns message with the correctly offset code.
   */
  public MessageData multiplex(final Capability cap, final MessageData messageToSend) {
    final int offset = null == cap ? 0 : capabilityOffsets.get(cap);
    return offsetMessageCode(messageToSend, offset);
  }

  /**
   * Given a message from a peer, determine which capability the message corresponds to and maps the
   * message code to the appropriate value.
   *
   * @param receivedMessage The message received from a peer.
   * @return The interpreted message.
   */
  public ProtocolMessage demultiplex(final MessageData receivedMessage) {
    final Map.Entry<Range<Integer>, Capability> agreedCap =
        agreedCaps.getEntry(receivedMessage.getCode());

    if (agreedCap == null) {
      return new ProtocolMessage(null, receivedMessage);
    }

    final int offset = -agreedCap.getKey().lowerEndpoint();
    final Capability cap = agreedCap.getValue();

    final MessageData demultiplexedMessage = offsetMessageCode(receivedMessage, offset);
    return new ProtocolMessage(cap, demultiplexedMessage);
  }

  private MessageData offsetMessageCode(final MessageData originalMessage, final int offset) {
    // Return wrapped message with modified offset
    return new MessageData() {
      @Override
      public int getSize() {
        return originalMessage.getSize();
      }

      @Override
      public int getCode() {
        return originalMessage.getCode() + offset;
      }

      @Override
      public Bytes getData() {
        return originalMessage.getData();
      }
    };
  }

  private ImmutableRangeMap<Integer, Capability> calculateAgreedCapabilities(
      final List<Capability> a, final List<Capability> b) {
    final List<Capability> caps = new ArrayList<>(a);
    caps.sort(CAPABILITY_COMPARATOR);
    caps.retainAll(b);

    final ImmutableRangeMap.Builder<Integer, Capability> builder = ImmutableRangeMap.builder();
    // Reserve some messages for WireProtocol
    int offset = WIRE_PROTOCOL_MESSAGE_SPACE;
    String prevProtocol = null;
    for (final Iterator<Capability> itr = caps.iterator(); itr.hasNext(); ) {
      final Capability cap = itr.next();
      final String curProtocol = cap.getName();
      if (curProtocol.equalsIgnoreCase(prevProtocol)) {
        // A later version of this protocol is already being used, so ignore this version
        continue;
      }
      prevProtocol = curProtocol;
      final SubProtocol subProtocol = subProtocols.get(cap.getName());
      final int messageSpace = subProtocol == null ? 0 : subProtocol.messageSpace(cap.getVersion());
      if (messageSpace > 0) {
        builder.put(Range.closedOpen(offset, offset + messageSpace), cap);
      }
      offset += messageSpace;
    }

    return builder.build();
  }

  private static ImmutableMap<Capability, Integer> calculateCapabilityOffsets(
      final ImmutableRangeMap<Integer, Capability> agreedCaps) {
    final ImmutableMap.Builder<Capability, Integer> capToOffset = ImmutableMap.builder();
    for (final Map.Entry<Range<Integer>, Capability> entry :
        agreedCaps.asMapOfRanges().entrySet()) {
      capToOffset.put(entry.getValue(), entry.getKey().lowerEndpoint());
    }
    return capToOffset.build();
  }

  public static class ProtocolMessage {
    private final Capability capability;
    private final MessageData message;

    ProtocolMessage(final Capability capability, final MessageData message) {
      this.capability = capability;
      this.message = message;
    }

    public Capability getCapability() {
      return capability;
    }

    public MessageData getMessage() {
      return message;
    }

    @Override
    public String toString() {
      return "ProtocolMessage{"
          + "capability="
          + capability
          + ", messageCode="
          + message.getCode()
          + '}';
    }
  }
}
