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
package org.hyperledger.besu.util.era1;

import org.hyperledger.besu.util.snappy.SnappyFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xerial.snappy.SnappyFramedInputStream;

@ExtendWith(MockitoExtension.class)
public class Era1ReaderTest {

  private @Mock SnappyFactory snappyFactory;

  private Era1Reader reader;

  @BeforeEach
  public void beforeTest() {
    reader = new Era1Reader(snappyFactory);
  }

  @Test
  public void testReadForVersionType() throws IOException {
    InputStream inputStream = Mockito.mock(InputStream.class);
    Era1ReaderListener listener = Mockito.mock(Era1ReaderListener.class);

    Mockito.when(inputStream.available()).thenReturn(8, 0);
    Mockito.when(inputStream.readNBytes(2)).thenReturn(Era1Type.VERSION.getTypeCode());
    Mockito.when(inputStream.readNBytes(6))
        .thenReturn(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00});

    reader.read(inputStream, listener);

    Mockito.verifyNoInteractions(snappyFactory);
    Mockito.verify(inputStream, Mockito.times(2)).available();
    Mockito.verify(inputStream).readNBytes(2);
    Mockito.verify(inputStream).readNBytes(6);
    Mockito.verifyNoInteractions(listener);
  }

  @Test
  public void testReadForEmptyType() throws IOException {
    InputStream inputStream = Mockito.mock(InputStream.class);
    Era1ReaderListener listener = Mockito.mock(Era1ReaderListener.class);

    Mockito.when(inputStream.available()).thenReturn(16, 0);
    Mockito.when(inputStream.readNBytes(2)).thenReturn(Era1Type.EMPTY.getTypeCode());
    Mockito.when(inputStream.readNBytes(6))
        .thenReturn(new byte[] {0x08, 0x00, 0x00, 0x00, 0x00, 0x00});

    reader.read(inputStream, listener);

    Mockito.verifyNoInteractions(snappyFactory);
    Mockito.verify(inputStream, Mockito.times(2)).available();
    Mockito.verify(inputStream).readNBytes(2);
    Mockito.verify(inputStream).readNBytes(6);
    Mockito.verify(inputStream).skipNBytes(8);
    Mockito.verifyNoInteractions(listener);
  }

  @Test
  public void testReadForCompressedExecutionBlockHeader() throws IOException {
    InputStream inputStream = Mockito.mock(InputStream.class);
    Era1ReaderListener listener = Mockito.mock(Era1ReaderListener.class);
    SnappyFramedInputStream snappyFramedInputStream = Mockito.mock(SnappyFramedInputStream.class);

    Mockito.when(inputStream.available()).thenReturn(15, 0);
    Mockito.when(inputStream.readNBytes(2))
        .thenReturn(Era1Type.COMPRESSED_EXECUTION_BLOCK_HEADER.getTypeCode());
    Mockito.when(inputStream.readNBytes(6))
        .thenReturn(new byte[] {0x07, 0x00, 0x00, 0x00, 0x00, 0x00});
    byte[] compressedExecutionBlockHeader = new byte[] {1, 2, 3, 4, 5, 6, 7};
    Mockito.when(inputStream.readNBytes(7)).thenReturn(compressedExecutionBlockHeader);
    Mockito.when(snappyFactory.createFramedInputStream(compressedExecutionBlockHeader))
        .thenReturn(snappyFramedInputStream);
    byte[] executionBlockHeader = new byte[] {10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    Mockito.when(snappyFramedInputStream.readAllBytes()).thenReturn(executionBlockHeader);

    reader.read(inputStream, listener);

    Mockito.verify(inputStream, Mockito.times(2)).available();
    Mockito.verify(inputStream).readNBytes(2);
    Mockito.verify(inputStream).readNBytes(6);
    Mockito.verify(inputStream).readNBytes(7);
    Mockito.verify(snappyFactory).createFramedInputStream(compressedExecutionBlockHeader);
    Mockito.verify(snappyFramedInputStream).readAllBytes();
    ArgumentCaptor<Era1ExecutionBlockHeader> executionBlockHeaderArgumentCaptor =
        ArgumentCaptor.forClass(Era1ExecutionBlockHeader.class);
    Mockito.verify(listener)
        .handleExecutionBlockHeader(executionBlockHeaderArgumentCaptor.capture());
    Mockito.verifyNoMoreInteractions(listener);

    Era1ExecutionBlockHeader era1ExecutionBlockHeader =
        executionBlockHeaderArgumentCaptor.getValue();
    Assertions.assertEquals(executionBlockHeader, era1ExecutionBlockHeader.header());
    Assertions.assertEquals(0, era1ExecutionBlockHeader.blockIndex());
  }

  @Test
  public void testReadForCompressedExecutionBlockBody() throws IOException {
    InputStream inputStream = Mockito.mock(InputStream.class);
    Era1ReaderListener listener = Mockito.mock(Era1ReaderListener.class);
    SnappyFramedInputStream snappyFramedInputStream = Mockito.mock(SnappyFramedInputStream.class);

    Mockito.when(inputStream.available()).thenReturn(15, 0);
    Mockito.when(inputStream.readNBytes(2))
        .thenReturn(Era1Type.COMPRESSED_EXECUTION_BLOCK_BODY.getTypeCode());
    Mockito.when(inputStream.readNBytes(6))
        .thenReturn(new byte[] {0x07, 0x00, 0x00, 0x00, 0x00, 0x00});
    byte[] compressedExecutionBlockBody = new byte[] {1, 2, 3, 4, 5, 6, 7};
    Mockito.when(inputStream.readNBytes(7)).thenReturn(compressedExecutionBlockBody);
    Mockito.when(snappyFactory.createFramedInputStream(compressedExecutionBlockBody))
        .thenReturn(snappyFramedInputStream);
    byte[] executionBlockBody = new byte[] {10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    Mockito.when(snappyFramedInputStream.readAllBytes()).thenReturn(executionBlockBody);

    reader.read(inputStream, listener);

    Mockito.verify(inputStream, Mockito.times(2)).available();
    Mockito.verify(inputStream).readNBytes(2);
    Mockito.verify(inputStream).readNBytes(6);
    Mockito.verify(inputStream).readNBytes(7);
    Mockito.verify(snappyFactory).createFramedInputStream(compressedExecutionBlockBody);
    Mockito.verify(snappyFramedInputStream).readAllBytes();
    ArgumentCaptor<Era1ExecutionBlockBody> executionBlockBodyArgumentCaptor =
        ArgumentCaptor.forClass(Era1ExecutionBlockBody.class);
    Mockito.verify(listener).handleExecutionBlockBody(executionBlockBodyArgumentCaptor.capture());
    Mockito.verifyNoMoreInteractions(listener);

    Era1ExecutionBlockBody era1ExecutionBlockBody = executionBlockBodyArgumentCaptor.getValue();
    Assertions.assertEquals(executionBlockBody, era1ExecutionBlockBody.block());
    Assertions.assertEquals(0, era1ExecutionBlockBody.blockIndex());
  }

  @Test
  public void testReadForCompressedExecutionBlockReceipts() throws IOException {
    InputStream inputStream = Mockito.mock(InputStream.class);
    Era1ReaderListener listener = Mockito.mock(Era1ReaderListener.class);
    SnappyFramedInputStream snappyFramedInputStream = Mockito.mock(SnappyFramedInputStream.class);

    Mockito.when(inputStream.available()).thenReturn(15, 0);
    Mockito.when(inputStream.readNBytes(2))
        .thenReturn(Era1Type.COMPRESSED_EXECUTION_BLOCK_RECEIPTS.getTypeCode());
    Mockito.when(inputStream.readNBytes(6))
        .thenReturn(new byte[] {0x07, 0x00, 0x00, 0x00, 0x00, 0x00});
    byte[] compressedExecutionBlockReceipts = new byte[] {1, 2, 3, 4, 5, 6, 7};
    Mockito.when(inputStream.readNBytes(7)).thenReturn(compressedExecutionBlockReceipts);
    Mockito.when(snappyFactory.createFramedInputStream(compressedExecutionBlockReceipts))
        .thenReturn(snappyFramedInputStream);
    byte[] executionBlockReceipts = new byte[] {10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    Mockito.when(snappyFramedInputStream.readAllBytes()).thenReturn(executionBlockReceipts);

    reader.read(inputStream, listener);

    Mockito.verify(inputStream, Mockito.times(2)).available();
    Mockito.verify(inputStream).readNBytes(2);
    Mockito.verify(inputStream).readNBytes(6);
    Mockito.verify(inputStream).readNBytes(7);
    Mockito.verify(snappyFactory).createFramedInputStream(compressedExecutionBlockReceipts);
    Mockito.verify(snappyFramedInputStream).readAllBytes();
    ArgumentCaptor<Era1ExecutionBlockReceipts> executionBlockReceiptsArgumentCaptor =
        ArgumentCaptor.forClass(Era1ExecutionBlockReceipts.class);
    Mockito.verify(listener)
        .handleExecutionBlockReceipts(executionBlockReceiptsArgumentCaptor.capture());
    Mockito.verifyNoMoreInteractions(listener);

    Era1ExecutionBlockReceipts era1ExecutionBlockReceipts =
        executionBlockReceiptsArgumentCaptor.getValue();
    Assertions.assertEquals(executionBlockReceipts, era1ExecutionBlockReceipts.receipts());
    Assertions.assertEquals(0, era1ExecutionBlockReceipts.blockIndex());
  }

  @Test
  public void testReadForBlockIndexType() throws IOException {
    InputStream inputStream = Mockito.mock(InputStream.class);
    Era1ReaderListener listener = Mockito.mock(Era1ReaderListener.class);

    Mockito.when(inputStream.available()).thenReturn(40, 0);
    Mockito.when(inputStream.readNBytes(2)).thenReturn(Era1Type.BLOCK_INDEX.getTypeCode());
    Mockito.when(inputStream.readNBytes(6))
        .thenReturn(new byte[] {0x20, 0x00, 0x00, 0x00, 0x00, 0x00});
    Mockito.when(inputStream.readNBytes(32))
        .thenReturn(
            new byte[] {
              0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00,
              0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00,
              0x00, 0x00, 0x00, 0x00
            });

    reader.read(inputStream, listener);

    Mockito.verifyNoInteractions(snappyFactory);
    Mockito.verify(inputStream, Mockito.times(2)).available();
    Mockito.verify(inputStream).readNBytes(2);
    Mockito.verify(inputStream).readNBytes(6);
    Mockito.verify(inputStream).readNBytes(32);
    ArgumentCaptor<Era1BlockIndex> blockIndexArgumentCaptor =
        ArgumentCaptor.forClass(Era1BlockIndex.class);
    Mockito.verify(listener).handleBlockIndex(blockIndexArgumentCaptor.capture());
    Mockito.verifyNoMoreInteractions(listener);

    Era1BlockIndex blockIndex = blockIndexArgumentCaptor.getValue();
    Assertions.assertEquals(1, blockIndex.startingBlockIndex());
    Assertions.assertEquals(List.of(2L, 3L), blockIndex.indexes());
  }
}
