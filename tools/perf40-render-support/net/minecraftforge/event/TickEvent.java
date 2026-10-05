// TEST ADAPTER ONLY. Not shipped in the mod JAR. No actual Forge/GL emulation.
package net.minecraftforge.event;public class TickEvent {public enum Phase {START,END}public static class ClientTickEvent {public Phase phase=Phase.END;}}
