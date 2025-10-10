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
package org.hyperledger.besu.chainexport;

import org.hyperledger.besu.util.era1.Era1Type;
import org.hyperledger.besu.util.snappy.SnappyFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xerial.snappy.SnappyFramedOutputStream;

@ExtendWith(MockitoExtension.class)
public class Era1FileWriterTest {
  private @Mock FileOutputStream writer;
  private @Mock SnappyFactory snappyFactory;

  private Era1FileWriter era1FileWriter;

  @BeforeEach
  public void beforeTest() {
    era1FileWriter = new Era1FileWriter(writer, snappyFactory);
  }

  @Test
  public void testWriteSectionForVersion() throws IOException {
    era1FileWriter.writeSection(Era1Type.VERSION, new byte[] {});

    Mockito.verify(writer)
        .write(Mockito.argThat(new ByteArrayArgumentMatcher(Era1Type.VERSION.getTypeCode())));
    Mockito.verify(writer)
        .write(Mockito.argThat(new ByteArrayArgumentMatcher(new byte[] {0, 0, 0, 0, 0, 0})));
    Mockito.verify(writer).write(Mockito.argThat(new ByteArrayArgumentMatcher(new byte[] {})));

    Assertions.assertEquals(8, era1FileWriter.getPosition());
  }

  @Test
  public void testWriteSectionForAccumulator() throws IOException {
    byte[] accumulatorHash = Bytes32.random().toArray();
    era1FileWriter.writeSection(Era1Type.ACCUMULATOR, accumulatorHash);

    Mockito.verify(writer)
        .write(Mockito.argThat(new ByteArrayArgumentMatcher(Era1Type.ACCUMULATOR.getTypeCode())));
    Mockito.verify(writer)
        .write(Mockito.argThat(new ByteArrayArgumentMatcher(new byte[] {32, 0, 0, 0, 0, 0})));
    Mockito.verify(writer).write(Mockito.argThat(new ByteArrayArgumentMatcher(accumulatorHash)));

    Assertions.assertEquals(40, era1FileWriter.getPosition());
  }

  @Test
  public void testWriteSectionForCompressedHeader() throws IOException {
    byte[] uncompressedHeader = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    byte[] compressedHeader = new byte[] {10, 11};

    SnappyFramedOutputStream snappyOutputStream = Mockito.mock(SnappyFramedOutputStream.class);
    Mockito.when(snappyFactory.createFramedOutputStream(Mockito.any(ByteArrayOutputStream.class)))
        .thenAnswer(
            (invocationOnMock) -> {
              ByteArrayOutputStream byteArrayOutputStream =
                  invocationOnMock.getArgument(0, ByteArrayOutputStream.class);
              byteArrayOutputStream.write(compressedHeader);
              return snappyOutputStream;
            });

    era1FileWriter.writeSection(Era1Type.COMPRESSED_EXECUTION_BLOCK_HEADER, uncompressedHeader);

    Mockito.verify(snappyFactory)
        .createFramedOutputStream(Mockito.any(ByteArrayOutputStream.class));
    Mockito.verify(snappyOutputStream).write(uncompressedHeader);

    Mockito.verify(writer)
        .write(
            Mockito.argThat(
                new ByteArrayArgumentMatcher(
                    Era1Type.COMPRESSED_EXECUTION_BLOCK_HEADER.getTypeCode())));
    Mockito.verify(writer)
        .write(Mockito.argThat(new ByteArrayArgumentMatcher(new byte[] {2, 0, 0, 0, 0, 0})));
    Mockito.verify(writer).write(Mockito.argThat(new ByteArrayArgumentMatcher(compressedHeader)));

    Assertions.assertEquals(10, era1FileWriter.getPosition());
  }

  private static class ByteArrayArgumentMatcher implements ArgumentMatcher<byte[]> {
    private final byte[] expectedBytes;

    public ByteArrayArgumentMatcher(final byte[] expectedBytes) {
      this.expectedBytes = expectedBytes;
    }

    @Override
    public boolean matches(final byte[] actualBytes) {
      if (expectedBytes.length != actualBytes.length) {
        return false;
      }
      for (int i = 0; i < expectedBytes.length; i++) {
        if (expectedBytes[i] != actualBytes[i]) {
          return false;
        }
      }
      return true;
    }
  }
}
