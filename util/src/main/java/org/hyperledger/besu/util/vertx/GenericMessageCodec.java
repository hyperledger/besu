/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.util.vertx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import org.slf4j.Logger;

/**
 * With a message codec you can pass any other type across the Vert.x event bus. This class can be
 * used as a generic message codec for any class.
 *
 * @param <T> the type of the message
 */
public class GenericMessageCodec<T> implements MessageCodec<T, T> {
  private final Class<T> objectClass;

  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger logger;

  /**
   * @param objectClass the type of the message
   * @param logger the logger to output errors
   */
  public GenericMessageCodec(final Class<T> objectClass, final Logger logger) {
    this.objectClass = objectClass;
    this.logger = logger;
  }

  @Override
  public void encodeToWire(final Buffer buffer, final T t) {
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    try {
      final ObjectOutput objectOutput = new ObjectOutputStream(outputStream);
      objectOutput.writeObject(t);
      objectOutput.flush();

      byte[] messageBytes = outputStream.toByteArray();
      buffer.appendInt(messageBytes.length);
      buffer.appendBytes(messageBytes);

      objectOutput.close();
      outputStream.close();
    } catch (IOException e) {
      logger.error("Error while encoding object to wire: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public T decodeFromWire(final int pos, final Buffer buffer) {
    int currentPos = pos;

    // length of encoded object
    final int messageLength = buffer.getInt(currentPos);

    // move position 4 bytes, because the length is int and is 4 bytes long
    currentPos += 4;

    final byte[] messageBytes = buffer.getBytes(currentPos, currentPos + messageLength);
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(messageBytes);

    final T message;

    try {
      final ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
      message = (T) objectInputStream.readObject();
      objectInputStream.close();
      inputStream.close();
    } catch (IOException | ClassNotFoundException e) {
      logger.error("Error while decoding object from wire: {}", e.getMessage());
      throw new RuntimeException(e);
    }

    return message;
  }

  @Override
  public T transform(final T message) {
    return message;
  }

  @Override
  public String name() {
    return objectClass.getSimpleName() + "MessageCodec";
  }

  @Override
  public byte systemCodecID() {
    return -1;
  }
}
