package org.hyperledger.besu.util.io;

import org.hyperledger.besu.util.e2.E2SlotIndex;
import org.hyperledger.besu.util.e2.E2StoreReaderListener;
import org.hyperledger.besu.util.e2.E2Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class E2StoreReaderTest {

    private final E2StoreReader reader = new E2StoreReader();

    @Test
    public void testReadForVersionType() throws IOException {
        InputStream inputStream = Mockito.mock(InputStream.class);
        E2StoreReaderListener listener = Mockito.mock(E2StoreReaderListener.class);

        Mockito.when(inputStream.available()).thenReturn(8, 0);
        Mockito.when(inputStream.readNBytes(2)).thenReturn(E2Type.VERSION.getTypeCode());
        Mockito.when(inputStream.readNBytes(6)).thenReturn(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00});

        reader.read(inputStream, listener);

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

        Mockito.when(inputStream.available()).thenReturn(8, 0);
        Mockito.when(inputStream.readNBytes(2)).thenReturn(E2Type.SLOT_INDEX.getTypeCode());
        Mockito.when(inputStream.readNBytes(6)).thenReturn(new byte[]{0x20, 0x00, 0x00, 0x00, 0x00, 0x00});
        Mockito.when(inputStream.readNBytes(32)).thenReturn(new byte[]{0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});

        reader.read(inputStream, listener);

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
}
