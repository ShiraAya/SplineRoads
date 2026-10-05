// TEST ADAPTER ONLY. Not shipped in the mod JAR. No actual Forge/GL emulation.
package net.minecraftforge.fml.common;public @interface Mod {String value() default "";public @interface EventBusSubscriber {String modid();net.minecraftforge.api.distmarker.Dist[] value();Bus bus() default Bus.FORGE;enum Bus {MOD,FORGE}}}
