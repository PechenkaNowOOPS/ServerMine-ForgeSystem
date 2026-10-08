package ru.servermine.forge;

/** All GUI bitmap layers share a 256px canvas and a fixed 257px advance. */
public final class ForgeGuiGlyphRegistry {
    public static final String FONT="servermine:forge";
    public static final char BASE='\uE001',LABELS='\uE002',HEAT='\uE020',DIGITS='\uE100',STATUS='\uE050';
    public static final char FUEL_TIME='\uE200',FUEL_COAL='\uE300',FUEL_OVERFLOW='\uE340';
    public static final char LEFT='\uE700',BACK='\uE701';
    private ForgeGuiGlyphRegistry() {}
}
