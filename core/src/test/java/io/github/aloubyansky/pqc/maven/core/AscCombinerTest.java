package io.github.aloubyansky.pqc.maven.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AscCombinerTest {

    // Generate valid armored blocks for testing
    // These are structurally valid (proper CRC) but cryptographically meaningless
    private static final String ARMORED_BLOCK_1 = createTestBlock1();
    private static final String ARMORED_BLOCK_2 = createTestBlock2();

    private static String createTestBlock1() {
        // Create a small test packet with mock data
        byte[] testData = new byte[] {
            (byte) 0x88, 0x5E, 0x04, 0x00, 0x11, 0x08, 0x00, 0x06,
            0x05, 0x02, 0x61, 0x74, 0x00, 0x09, 0x00, 0x0A, 0x09, 0x10
        };
        return AscCombiner.armor(testData);
    }

    private static String createTestBlock2() {
        // Create a different small test packet with mock data
        byte[] testData = new byte[] {
            (byte) 0x88, 0x75, 0x04, 0x00, 0x16, 0x0A, 0x00, 0x06,
            0x05, 0x02, 0x61, 0x74, 0x00, 0x0A, 0x00, 0x0A, 0x09, 0x11
        };
        return AscCombiner.armor(testData);
    }

    @Test
    void combine_mergesTwoArmoredBlocks() {
        String combined = AscCombiner.combine(ARMORED_BLOCK_1, ARMORED_BLOCK_2);

        assertNotNull(combined);

        // Verify result has exactly one BEGIN/END marker pair
        int beginCount = countOccurrences(combined, "-----BEGIN PGP SIGNATURE-----");
        int endCount = countOccurrences(combined, "-----END PGP SIGNATURE-----");

        assertEquals(1, beginCount, "Combined block should have exactly one BEGIN marker");
        assertEquals(1, endCount, "Combined block should have exactly one END marker");
    }

    @Test
    void combine_resultIsLargerThanEitherInput() {
        String combined = AscCombiner.combine(ARMORED_BLOCK_1, ARMORED_BLOCK_2);

        assertNotNull(combined);

        // Combined result should be larger than either input alone
        int combinedLength = combined.length();
        assertTrue(combinedLength > ARMORED_BLOCK_1.length(),
                "Combined result should be larger than first input");
        assertTrue(combinedLength > ARMORED_BLOCK_2.length(),
                "Combined result should be larger than second input");
    }

    @Test
    void dearmor_extractsRawBytes() {
        byte[] raw = AscCombiner.dearmor(ARMORED_BLOCK_1);

        assertNotNull(raw, "Dearmored result should not be null");
        assertTrue(raw.length > 0, "Dearmored result should not be empty");
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
