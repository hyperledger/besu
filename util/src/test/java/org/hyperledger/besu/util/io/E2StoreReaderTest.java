package org.hyperledger.besu.util.io;

import org.hyperledger.besu.util.e2.E2BeaconState;
import org.hyperledger.besu.util.e2.E2SignedBeaconBlock;
import org.hyperledger.besu.util.e2.E2SlotIndex;
import org.hyperledger.besu.util.e2.E2StoreReaderListener;
import org.hyperledger.besu.util.e2.E2Type;
import org.hyperledger.besu.util.snappy.SnappyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xerial.snappy.SnappyFramedInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class E2StoreReaderTest {

    private @Mock SnappyFactory snappyFactory;

    private E2StoreReader reader;

    @BeforeEach
    public void beforeTest() {
        reader = new E2StoreReader(snappyFactory);
    }

    @Test
    public void testReadForVersionType() throws IOException {
        InputStream inputStream = Mockito.mock(InputStream.class);
        E2StoreReaderListener listener = Mockito.mock(E2StoreReaderListener.class);

        Mockito.when(inputStream.available()).thenReturn(8, 0);
        Mockito.when(inputStream.readNBytes(2)).thenReturn(E2Type.VERSION.getTypeCode());
        Mockito.when(inputStream.readNBytes(6)).thenReturn(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00});

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
        E2StoreReaderListener listener = Mockito.mock(E2StoreReaderListener.class);

        Mockito.when(inputStream.available()).thenReturn(16, 0);
        Mockito.when(inputStream.readNBytes(2)).thenReturn(E2Type.EMPTY.getTypeCode());
        Mockito.when(inputStream.readNBytes(6)).thenReturn(new byte[]{0x08, 0x00, 0x00, 0x00, 0x00, 0x00});

        reader.read(inputStream, listener);

        Mockito.verifyNoInteractions(snappyFactory);
        Mockito.verify(inputStream, Mockito.times(2)).available();
        Mockito.verify(inputStream).readNBytes(2);
        Mockito.verify(inputStream).readNBytes(6);
        Mockito.verify(inputStream).skipNBytes(8);
        Mockito.verifyNoInteractions(listener);
    }

    @Test
    public void testReadForSlotIndexType() throws IOException {
        InputStream inputStream = Mockito.mock(InputStream.class);
        E2StoreReaderListener listener = Mockito.mock(E2StoreReaderListener.class);

        Mockito.when(inputStream.available()).thenReturn(40, 0);
        Mockito.when(inputStream.readNBytes(2)).thenReturn(E2Type.SLOT_INDEX.getTypeCode());
        Mockito.when(inputStream.readNBytes(6)).thenReturn(new byte[]{0x20, 0x00, 0x00, 0x00, 0x00, 0x00});
        Mockito.when(inputStream.readNBytes(32)).thenReturn(new byte[]{0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});

        reader.read(inputStream, listener);

        Mockito.verifyNoInteractions(snappyFactory);
        Mockito.verify(inputStream, Mockito.times(2)).available();
        Mockito.verify(inputStream).readNBytes(2);
        Mockito.verify(inputStream).readNBytes(6);
        Mockito.verify(inputStream).readNBytes(32);
        ArgumentCaptor<E2SlotIndex> slotIndexArgumentCaptor = ArgumentCaptor.forClass(E2SlotIndex.class);
        Mockito.verify(listener).handleSlotIndex(slotIndexArgumentCaptor.capture());
        Mockito.verifyNoMoreInteractions(listener);

        E2SlotIndex slotIndex = slotIndexArgumentCaptor.getValue();
        Assertions.assertEquals(1, slotIndex.getStartingSlot());
        Assertions.assertEquals(List.of(2L, 3L), slotIndex.getIndexes());
    }

    @Test
    public void testReadForCompressedBeaconState() throws IOException {
        InputStream inputStream = Mockito.mock(InputStream.class);
        E2StoreReaderListener listener = Mockito.mock(E2StoreReaderListener.class);
        SnappyFramedInputStream snappyFramedInputStream = Mockito.mock(SnappyFramedInputStream.class);

        Mockito.when(inputStream.available()).thenReturn(15, 0);
        Mockito.when(inputStream.readNBytes(2)).thenReturn(E2Type.COMPRESSED_BEACON_STATE.getTypeCode());
        Mockito.when(inputStream.readNBytes(6)).thenReturn(new byte[]{0x07, 0x00, 0x00, 0x00, 0x00, 0x00});
        byte[] compressedBeaconState = new byte[]{1, 2, 3, 4, 5, 6, 7};
        Mockito.when(inputStream.readNBytes(7)).thenReturn(compressedBeaconState);
        Mockito.when(snappyFactory.createFramedInputStream(compressedBeaconState)).thenReturn(snappyFramedInputStream);
        byte[] beaconState = new byte[]{10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
        Mockito.when(snappyFramedInputStream.readAllBytes()).thenReturn(beaconState);

        reader.read(inputStream, listener);

        Mockito.verify(inputStream, Mockito.times(2)).available();
        Mockito.verify(inputStream).readNBytes(2);
        Mockito.verify(inputStream).readNBytes(6);
        Mockito.verify(inputStream).readNBytes(7);
        Mockito.verify(snappyFactory).createFramedInputStream(compressedBeaconState);
        Mockito.verify(snappyFramedInputStream).readAllBytes();
        ArgumentCaptor<E2BeaconState> beaconStateArgumentCaptor = ArgumentCaptor.forClass(E2BeaconState.class);
        Mockito.verify(listener).handleBeaconState(beaconStateArgumentCaptor.capture());
        Mockito.verifyNoMoreInteractions(listener);

        E2BeaconState e2BeaconState = beaconStateArgumentCaptor.getValue();
        Assertions.assertEquals(beaconState, e2BeaconState.getBeaconState());
        Assertions.assertEquals(0, e2BeaconState.getSlot());
    }

    @Test
    public void testReadForCompressedBeaconStateMultipleReadsIncrementsSlot() throws IOException {
        InputStream inputStream = Mockito.mock(InputStream.class);
        E2StoreReaderListener listener = Mockito.mock(E2StoreReaderListener.class);
        SnappyFramedInputStream snappyFramedInputStream1 = Mockito.mock(SnappyFramedInputStream.class);
        SnappyFramedInputStream snappyFramedInputStream2 = Mockito.mock(SnappyFramedInputStream.class);

        Mockito.when(inputStream.available()).thenReturn(30, 15, 0);
        Mockito.when(inputStream.readNBytes(2)).thenReturn(E2Type.COMPRESSED_BEACON_STATE.getTypeCode(), E2Type.COMPRESSED_BEACON_STATE.getTypeCode());
        Mockito.when(inputStream.readNBytes(6)).thenReturn(new byte[]{0x07, 0x00, 0x00, 0x00, 0x00, 0x00}, new byte[]{0x07, 0x00, 0x00, 0x00, 0x00, 0x00});
        byte[] compressedBeaconState = new byte[]{1, 2, 3, 4, 5, 6, 7};
        Mockito.when(inputStream.readNBytes(7)).thenReturn(compressedBeaconState, compressedBeaconState);
        Mockito.when(snappyFactory.createFramedInputStream(compressedBeaconState)).thenReturn(snappyFramedInputStream1, snappyFramedInputStream2);
        byte[] beaconState = new byte[]{10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
        Mockito.when(snappyFramedInputStream1.readAllBytes()).thenReturn(beaconState);
        Mockito.when(snappyFramedInputStream2.readAllBytes()).thenReturn(beaconState);

        reader.read(inputStream, listener);

        Mockito.verify(inputStream, Mockito.times(3)).available();
        Mockito.verify(inputStream, Mockito.times(2)).readNBytes(2);
        Mockito.verify(inputStream, Mockito.times(2)).readNBytes(6);
        Mockito.verify(inputStream, Mockito.times(2)).readNBytes(7);
        Mockito.verify(snappyFactory, Mockito.times(2)).createFramedInputStream(compressedBeaconState);
        Mockito.verify(snappyFramedInputStream1).readAllBytes();
        Mockito.verify(snappyFramedInputStream2).readAllBytes();
        ArgumentCaptor<E2BeaconState> beaconStateArgumentCaptor = ArgumentCaptor.forClass(E2BeaconState.class);
        Mockito.verify(listener, Mockito.times(2)).handleBeaconState(beaconStateArgumentCaptor.capture());
        Mockito.verifyNoMoreInteractions(listener);

        List<E2BeaconState> e2BeaconStates = beaconStateArgumentCaptor.getAllValues();
        Assertions.assertEquals(2, e2BeaconStates.size());
        E2BeaconState e2BeaconState1 = e2BeaconStates.getFirst();
        Assertions.assertEquals(beaconState, e2BeaconState1.getBeaconState());
        Assertions.assertEquals(0, e2BeaconState1.getSlot());
        E2BeaconState e2BeaconState2 = e2BeaconStates.getLast();
        Assertions.assertEquals(beaconState, e2BeaconState2.getBeaconState());
        Assertions.assertEquals(1, e2BeaconState2.getSlot());
    }

    @Test
    public void testReadForCompressedSignedBeaconBlock() throws IOException {
        InputStream inputStream = Mockito.mock(InputStream.class);
        E2StoreReaderListener listener = Mockito.mock(E2StoreReaderListener.class);
        SnappyFramedInputStream snappyFramedInputStream = Mockito.mock(SnappyFramedInputStream.class);

        Mockito.when(inputStream.available()).thenReturn(15, 0);
        Mockito.when(inputStream.readNBytes(2)).thenReturn(E2Type.COMPRESSED_SIGNED_BEACON_BLOCK.getTypeCode());
        Mockito.when(inputStream.readNBytes(6)).thenReturn(new byte[]{0x07, 0x00, 0x00, 0x00, 0x00, 0x00});
        byte[] compressedSignedBeaconBlock = new byte[]{1, 2, 3, 4, 5, 6, 7};
        Mockito.when(inputStream.readNBytes(7)).thenReturn(compressedSignedBeaconBlock);
        Mockito.when(snappyFactory.createFramedInputStream(compressedSignedBeaconBlock)).thenReturn(snappyFramedInputStream);
        byte[] signedBeaconBlock = new byte[]{10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
        Mockito.when(snappyFramedInputStream.readAllBytes()).thenReturn(signedBeaconBlock);

        reader.read(inputStream, listener);

        Mockito.verify(inputStream, Mockito.times(2)).available();
        Mockito.verify(inputStream).readNBytes(2);
        Mockito.verify(inputStream).readNBytes(6);
        Mockito.verify(inputStream).readNBytes(7);
        Mockito.verify(snappyFactory).createFramedInputStream(compressedSignedBeaconBlock);
        Mockito.verify(snappyFramedInputStream).readAllBytes();
        ArgumentCaptor<E2SignedBeaconBlock> signedBeaconBlockArgumentCaptor = ArgumentCaptor.forClass(E2SignedBeaconBlock.class);
        Mockito.verify(listener).handleSignedBeaconBlock(signedBeaconBlockArgumentCaptor.capture());
        Mockito.verifyNoMoreInteractions(listener);

        E2SignedBeaconBlock e2SignedBeaconBlock = signedBeaconBlockArgumentCaptor.getValue();
        Assertions.assertEquals(signedBeaconBlock, e2SignedBeaconBlock.getSignedBeaconBlock());
        Assertions.assertEquals(0, e2SignedBeaconBlock.getSlot());
    }
}
