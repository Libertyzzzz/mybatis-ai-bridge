package com.nextify.mab.util;

/** Rough token estimate without pulling in a tokenizer: ~4 ASCII chars per token, ~1 token per CJK char. */
public final class Tokens {

    private Tokens() {
    }

    public static long estimate(CharSequence s) {
        long ascii = 0;
        long other = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < 128) {
                ascii++;
            } else {
                other++;
            }
        }
        return ascii / 4 + other;
    }
}
