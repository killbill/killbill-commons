package org.skife.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataAmount
{
    private final long value;
    private final DataAmountUnit unit;
    private final long numBytes; 
    private static final Pattern SPLIT = Pattern.compile("^(\\d+)\\s*([a-zA-Z]+)$");
    private static final Pattern NUM_ONLY = Pattern.compile("^(\\d+)$");

    public DataAmount(String spec)
    {
        Matcher m = SPLIT.matcher(spec);
        if (!m.matches()) {
            // #7: allow undecorated unit to mean basic bytes
            m = NUM_ONLY.matcher(spec);
            if (!m.matches()) {
                throw new IllegalArgumentException(String.format("%s is not a valid data amount", spec));
            }
            unit = DataAmountUnit.BYTE;
            value = numBytes = Long.parseLong(spec);
        } else {
            String number = m.group(1);
            String type = m.group(2);
            this.value = Long.parseLong(number);
            this.unit = DataAmountUnit.fromString(type);
            this.numBytes = unit.getFactor() * value;
        }
    }

    public DataAmount(long value, DataAmountUnit unit)
    {
        this.value = value;
        this.unit = unit;
        this.numBytes = unit.getFactor() * value;
    }

    /**
     * @since 0.15
     */
    public DataAmount(long rawBytes)
    {
        value = numBytes = rawBytes;
        unit = DataAmountUnit.BYTE;
    }
    
    public long getValue()
    {
        return value;
    }

    public DataAmountUnit getUnit()
    {
        return unit;
    }

    public long getNumberOfBytes()
    {
        return numBytes;
    }

    public DataAmount convertTo(DataAmountUnit newUnit)
    {
        return new DataAmount(numBytes / newUnit.getFactor(), newUnit);
    }

    @Override
    public String toString()
    {
        return value + unit.getSymbol();
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int)(numBytes ^ (numBytes >>> 32));
        result = prime * result + unit.hashCode();
        result = prime * result + (int)(value ^ (value >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DataAmount other = (DataAmount)obj;

        return numBytes == other.numBytes;
    }
}
