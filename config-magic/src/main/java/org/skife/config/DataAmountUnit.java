package org.skife.config;

public enum DataAmountUnit
{
    BYTE("B", 1l),

    KIBIBYTE("KiB", 1024l),
    MEBIBYTE("MiB", 1024l*1024l),
    GIBIBYTE("GiB", 1024l*1024l*1024l),
    TEBIBYTE("TiB", 1024l*1024l*1024l*1024l),
    PEBIBYTE("PiB", 1024l*1024l*1024l*1024l*1024l),
    EXIBYTE("EiB", 1024l*1024l*1024l*1024l*1024l*1024l),

    KILOBYTE("kB", 1000l),
    MEGABYTE("MB", 1000l*1000l),
    GIGABYTE("GB", 1000l*1000l*1000l),
    TERABYTE("TB", 1000l*1000l*1000l*1000l),
    PETABYTE("PB", 1000l*1000l*1000l*1000l*1000l),
    EXABYTE("EB", 1000l*1000l*1000l*1000l*1000l*1000l);

    private final String symbol;
    private final long factor;

    private DataAmountUnit(String symbol, long factor)
    {
        this.symbol = symbol;
        this.factor = factor;
    }

    public String getSymbol()
    {
        return symbol;
    }

    public long getFactor()
    {
        return factor;
    }
    
    public static DataAmountUnit fromString(String text)
    {
        for (DataAmountUnit unit : DataAmountUnit.values()) {
            if (unit.symbol.equals(text)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unknown unit '" + text + "'");
    }

    public static DataAmountUnit fromStringCaseInsensitive(String origText)
    {
        final String text = origText.toLowerCase();
        for (DataAmountUnit unit : DataAmountUnit.values()) {
            if (unit.symbol.equals(text)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unknown unit '" + origText + "'");
    }
}
