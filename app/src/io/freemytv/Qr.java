package io.freemytv;

import java.nio.charset.StandardCharsets;

/**
 * A QR code for a short text (a web address): byte mode, versions 1 to 10, error correction level M, whichever of the 8
 * masks scores best. Written after the QR Code standard (ISO/IEC 18004); `modules(text)` returns the square of dark
 * (true) and light modules, without the quiet zone.
 */
final class Qr {
    private Qr() {}

    // Error correction codewords per block and number of blocks, level M, by version (index 0 unused).
    private static final int[] ECC_PER_BLOCK = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[] BLOCKS = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5};

    /** The modules, or null if the text doesn't fit in version 10. */
    static boolean[][] modules(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int version = 1;
        while (version <= 10 && capacityBytes(version) < data.length) version++;
        if (version > 10) return null;
        int size = version * 4 + 17;
        boolean[][] m = new boolean[size][size];
        boolean[][] fn = new boolean[size][size];
        drawFunctions(m, fn, version);
        byte[] codewords = interleave(encode(data, version), version);
        place(m, fn, codewords);
        int best = 0, bestScore = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            applyMask(m, fn, mask);
            drawFormat(m, fn, mask);
            int score = penalty(m);
            if (score < bestScore) {
                best = mask;
                bestScore = score;
            }
            applyMask(m, fn, mask);   // XOR again: undone
        }
        applyMask(m, fn, best);
        drawFormat(m, fn, best);
        return m;
    }

    // MARK: - Capacity and data

    private static int rawCodewords(int version) {
        int n = (16 * version + 128) * version + 64;
        if (version >= 2) {
            int align = version / 7 + 2;
            n -= (25 * align - 10) * align - 55;
            if (version >= 7) n -= 36;
        }
        return n / 8;
    }

    private static int dataCodewords(int version) {
        return rawCodewords(version) - ECC_PER_BLOCK[version] * BLOCKS[version];
    }

    private static int capacityBytes(int version) {
        int header = version < 10 ? 12 : 20;   // mode (4) + character count (8, or 16 from version 10)
        return (dataCodewords(version) * 8 - header) / 8;
    }

    private static byte[] encode(byte[] data, int version) {
        int capacityBits = dataCodewords(version) * 8;
        java.util.BitSet bits = new java.util.BitSet();
        int[] length = {0};
        put(bits, length, 4, 4);   // byte mode
        put(bits, length, data.length, version < 10 ? 8 : 16);
        for (byte b : data) put(bits, length, b & 0xFF, 8);
        put(bits, length, 0, Math.min(4, capacityBits - length[0]));
        while (length[0] % 8 != 0) put(bits, length, 0, 1);
        for (int pad = 0xEC; length[0] < capacityBits; pad ^= 0xEC ^ 0x11) put(bits, length, pad, 8);
        byte[] out = new byte[capacityBits / 8];
        for (int i = 0; i < out.length * 8; i++) if (bits.get(i)) out[i >> 3] |= 1 << (7 - (i & 7));
        return out;
    }

    private static void put(java.util.BitSet bits, int[] length, int value, int count) {
        for (int i = count - 1; i >= 0; i--) {
            if (((value >>> i) & 1) != 0) bits.set(length[0]);
            length[0]++;
        }
    }

    // MARK: - Error correction (Reed-Solomon over GF(256), polynomial 0x11D)

    private static byte[] interleave(byte[] data, int version) {
        int blocks = BLOCKS[version], eccLen = ECC_PER_BLOCK[version], raw = rawCodewords(version);
        int shortBlocks = blocks - raw % blocks, shortLen = raw / blocks - eccLen;
        byte[][] split = new byte[blocks][];
        byte[] divisor = divisor(eccLen);
        int at = 0;
        for (int i = 0; i < blocks; i++) {
            int len = shortLen + (i < shortBlocks ? 0 : 1);
            byte[] block = java.util.Arrays.copyOfRange(data, at, at + len);
            at += len;
            byte[] ecc = remainder(block, divisor);
            byte[] full = new byte[shortLen + 1 + eccLen];   // a short block leaves its data slot at shortLen empty
            System.arraycopy(block, 0, full, 0, shortLen);
            if (i >= shortBlocks) full[shortLen] = block[shortLen];
            System.arraycopy(ecc, 0, full, shortLen + 1, eccLen);
            split[i] = full;
        }
        byte[] out = new byte[raw];
        int o = 0;
        for (int i = 0; i < shortLen + 1 + eccLen; i++) {
            for (int j = 0; j < blocks; j++) {
                if (i != shortLen || j >= shortBlocks) out[o++] = split[j][i];
            }
        }
        return out;
    }

    private static byte[] divisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) multiply(result[j] & 0xFF, root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = multiply(root, 2);
        }
        return result;
    }

    private static byte[] remainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= multiply(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    private static int multiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    // MARK: - Drawing

    private static void set(boolean[][] m, boolean[][] fn, int x, int y, boolean dark) {
        m[y][x] = dark;
        fn[y][x] = true;
    }

    private static void drawFunctions(boolean[][] m, boolean[][] fn, int version) {
        int size = m.length;
        for (int i = 0; i < size; i++) {   // timing patterns
            set(m, fn, 6, i, i % 2 == 0);
            set(m, fn, i, 6, i % 2 == 0);
        }
        finder(m, fn, 3, 3);
        finder(m, fn, size - 4, 3);
        finder(m, fn, 3, size - 4);
        int[] pos = alignmentPositions(version);
        for (int i = 0; i < pos.length; i++) {
            for (int j = 0; j < pos.length; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == pos.length - 1) || (i == pos.length - 1 && j == 0)) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) set(m, fn, pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                }
            }
        }
        drawFormat(m, fn, 0);   // reserves the format areas (drawn again once the mask is known)
        if (version >= 7) {
            int rem = version;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            int bits = version << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean bit = ((bits >>> i) & 1) != 0;
                int a = size - 11 + i % 3, b = i / 3;
                set(m, fn, a, b, bit);
                set(m, fn, b, a, bit);
            }
        }
    }

    private static void finder(boolean[][] m, boolean[][] fn, int cx, int cy) {
        int size = m.length;
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) set(m, fn, x, y, dist != 2 && dist != 4);
            }
        }
    }

    private static int[] alignmentPositions(int version) {
        if (version == 1) return new int[0];
        int count = version / 7 + 2, size = version * 4 + 17;
        int step = (version * 8 + count * 3 + 5) / (count * 4 - 4) * 2;
        int[] out = new int[count];
        out[0] = 6;
        for (int i = count - 1, pos = size - 7; i >= 1; i--, pos -= step) out[i] = pos;
        return out;
    }

    /** Level M is format bits 0. */
    private static void drawFormat(boolean[][] m, boolean[][] fn, int mask) {
        int size = m.length, data = (0 << 3) | mask, rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(m, fn, 8, i, bit(bits, i));
        set(m, fn, 8, 7, bit(bits, 6));
        set(m, fn, 8, 8, bit(bits, 7));
        set(m, fn, 7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(m, fn, 14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(m, fn, size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(m, fn, 8, size - 15 + i, bit(bits, i));
        set(m, fn, 8, size - 8, true);
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    private static void place(boolean[][] m, boolean[][] fn, byte[] data) {
        int size = m.length, i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!fn[y][x] && i < data.length * 8) {
                        m[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private static void applyMask(boolean[][] m, boolean[][] fn, int mask) {
        int size = m.length;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (mask) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (invert && !fn[y][x]) m[y][x] = !m[y][x];
            }
        }
    }

    // MARK: - Which mask (the standard's four penalty rules)

    private static int penalty(boolean[][] m) {
        int size = m.length, score = 0, dark = 0;
        for (int pass = 0; pass < 2; pass++) {   // rows, then columns: runs of 5 or more, and the 1:1:3:1:1 pattern
            for (int a = 0; a < size; a++) {
                int run = 1;
                int history = 0;   // the last 7 modules as bits
                for (int b = 0; b < size; b++) {
                    boolean v = pass == 0 ? m[a][b] : m[b][a];
                    if (b > 0) {
                        boolean prev = pass == 0 ? m[a][b - 1] : m[b - 1][a];
                        if (v == prev) {
                            run++;
                            if (run == 5) score += 3;
                            else if (run > 5) score++;
                        } else {
                            run = 1;
                        }
                    }
                    history = ((history << 1) | (v ? 1 : 0)) & 0x7F;
                    if (b >= 6 && (history == 0x5D)) score += 40;   // 1011101 with light space on either side is tested below
                    if (v && pass == 0) dark++;
                }
            }
        }
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = m[y][x];
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) score += 3;
            }
        }
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        score += Math.max(0, k) * 10;
        return score;
    }
}
