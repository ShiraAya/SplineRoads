package net.minecraft.nbt;
public interface Tag {int TAG_BYTE=1,TAG_INT=3,TAG_LONG=4,TAG_DOUBLE=6,TAG_BYTE_ARRAY=7,TAG_STRING=8,TAG_LIST=9,TAG_COMPOUND=10,TAG_INT_ARRAY=11;default String getAsString(){return toString();}default Tag copy(){return this;} }
