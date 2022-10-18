package io.nosqlbench.virtdata.library.basics.shared.from_long.to_string;

import io.nosqlbench.virtdata.api.annotations.ThreadSafeMapper;

import java.util.function.LongFunction;

@ThreadSafeMapper
public class Pad implements LongFunction<String> {
    private static final String POSITION_BEFORE = "before";
    private static final String POSITION_AFTER = "after";

    private final String pattern;
    private final long patternLength;
    private final long totalLength;
    private final String position;

    public Pad(long totalLength) {
        this("0", totalLength);
    }

    public Pad(String pattern, long totalLength) {
        this(pattern, totalLength, POSITION_AFTER);
    }

    public Pad(String pattern, long totalLength, String position) {
        this.pattern = pattern;
        this.patternLength = pattern.length();
        this.totalLength = totalLength;
        this.position = position;
    }

    @Override
    public String apply(long value) {
        String valueAsString = Long.toString(value);

        long valueLength = valueAsString.length();
        long diff = totalLength - valueLength;

        if (diff > 0) {
            String padString = pattern.repeat((int) (diff / patternLength)) +
                pattern.substring(0, (int) (diff % patternLength));

            if (position.equals(POSITION_BEFORE)) {
                return valueAsString + padString;
            }

            if (position.equals(POSITION_AFTER)) {
                return padString + valueAsString;
            }
        }

        return valueAsString;
    }
}
