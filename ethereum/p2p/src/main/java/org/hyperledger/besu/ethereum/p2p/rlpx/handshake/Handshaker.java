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
package org.hyperledger.besu.ethereum.p2p.rlpx.handshake;

import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.ethereum.p2p.rlpx.handshake.ecies.ECIESHandshaker;

import java.util.Optional;

import io.netty.buffer.ByteBuf;

/**
 * A protocol to perform an RLPx crypto handshake with a peer.
 *
 * <p>This models a two-party handshake with a potentially indefinite sequence of messages between
 * parties, culminating with the creation of a {@link HandshakeSecrets} object containing the
 * secrets that have been agreed/generated as a result.
 *
 * <p>The roles modelled herein are that of an <em>initiator</em> and a <em>responder</em>. It is
 * assumed that the former is responsible for dispatching the first message, hence kicking off the
 * sequence. Nevertheless, implementations of this interface may choose to support concurrent
 * exchange of messages, as long as the backing crypto algorithms are capable of handling it.
 *
 * <p>When a party has no more messages to send, it signals so by returning an empty {@link
 * Optional} from the {@link #handleMessage(ByteBuf)} method. At this point, the consumer class is
 * expected to query the final result by calling {@link #getStatus()} and, if successful, it should
 * obtain the {@link HandshakeSecrets} outputs by calling {@link #secrets()}.
 *
 * <p>All methods can throw the {@link IllegalStateException} runtime exception if they're being
 * called at an illegal time. Refer to the methods Javadocs for more insight.
 *
 * <p>TODO: Declare a destroy() that securely destroys any intermediate secrets for security.
 *
 * @see ECIESHandshaker
 */
public interface Handshaker {

  /** Represents the status of the handshaker. */
  enum HandshakeStatus {

    /** This handshaker has been created but has not been prepared with the initial material. */
    UNINITIALIZED,

    /**
     * This handshaker has been prepared with the initial material, but the handshake is not yet in
     * progress.
     */
    PREPARED,

    /** The handshake is taking place. */
    IN_PROGRESS,

    /** The handshake culminated successfully, and the secrets have been generated. */
    SUCCESS,

    /** The handshake failed. */
    FAILED
  }

  /**
   * This method must be called by the <em>initiating side</em> of the handshake to provide the
   * initial crypto material for the handshake, before any further methods are called.
   *
   * <p>This method must throw an {@link IllegalStateException} exception if the handshake had
   * already been prepared before, no matter if under the initiator or the responder role.
   *
   * @param nodeKey An object which represents our identity
   * @param theirPubKey The public key of the node we're handshaking with.
   * @throws IllegalStateException Indicates that preparation had already occurred.
   */
  void prepareInitiator(NodeKey nodeKey, SECPPublicKey theirPubKey);

  /**
   * This method must be called by the <em>responding side</em> of the handshake to prepare the
   * initial crypto material for the handshake, before any further methods are called.
   *
   * <p>This method must throw an {@link IllegalStateException} exception if the handshake had
   * already been prepared before, whether with the initiator or the responder role.
   *
   * @param nodeKey An object which represents our identity
   * @throws IllegalStateException Indicates that preparation had already occurred.
   */
  void prepareResponder(NodeKey nodeKey);

  /**
   * Retrieves the first message to dispatch in the handshake ceremony.
   *
   * <p>This method <strong>must</strong> only be called by the party that's able to initiate the
   * handshake. In the {@link ECIESHandshaker initial implementation} of this interface, nobody but
   * the initiator is allowed to send the first message in the channel. Future implementations may
   * allow for a concurrent exchange.
   *
   * <p>This method will throw an {@link IllegalStateException} if the consumer has prepared this
   * handshake taking the role of the responder, and the underlying implementation only allows the
   * initiator to send the first message.
   *
   * @return The raw message to send, encrypted.
   * @throws IllegalStateException Indicates that this role taken by this party precludes it from
   *     sending the first message.
   * @throws HandshakeException Thrown if an error occurred during the encryption of the message.
   */
  ByteBuf firstMessage() throws HandshakeException;

  /**
   * Handles an encrypted incoming message, and produces an optional reply.
   *
   * <p>This method <strong>must</strong> be called whenever a message pertaining to this handshake
   * is received. Implementations are expected to mutate their underlying state accordingly. If the
   * handshake protocol defines a response message, it <strong>must</strong> be returned from the
   * call.
   *
   * <p>If the handshake has arrived at its final stage and no more messages are to be exchanged, an
   * empty optional <strong>must</strong> be returned. Consumers must then query the status by
   * calling {@link #getStatus()} and obtain the generated {@link HandshakeSecrets} if the status
   * allows it (i.e. success).
   *
   * @param buf The incoming message, encrypted.
   * @return The message to send in response, or an empty optional if there are no more messages to
   *     send and the handshake has arrived at its final stage.
   * @throws IllegalStateException Indicates that the handshake is not in progress.
   * @throws HandshakeException Thrown if an error occurred during the decryption of the incoming
   *     message or the encryption of the next message (if there is one).
   */
  Optional<ByteBuf> handleMessage(ByteBuf buf) throws HandshakeException;

  /**
   * Returns the current status of this handshake.
   *
   * @return The status of this handshake.
   */
  HandshakeStatus getStatus();

  /**
   * Returns the handshake secrets generated as a result of the handshake ceremony.
   *
   * @return The generated secrets.
   * @throws IllegalStateException Thrown if this handshake has not completed and hence it cannot
   *     return its secrets yet.
   */
  HandshakeSecrets secrets();

  /**
   * Returns the other party's public key, after the handshake has completed.
   *
   * @return The party's public key.
   * @throws IllegalStateException Thrown if this handshake has not completed and hence it cannot
   *     return the other party's public key yet.
   */
  SECPPublicKey partyPubKey();
}
