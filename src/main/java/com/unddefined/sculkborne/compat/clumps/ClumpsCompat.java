package com.unddefined.sculkborne.compat.clumps;

import com.unddefined.sculkborne.mixin.ExperienceOrbAccess;
import net.minecraft.world.entity.ExperienceOrb;
import org.jetbrains.annotations.Nullable;

/**
 * 与 Clumps 的兼容：读出一个经验球实际装着的总经验。
 *
 * <p>原版里一个球实体装着 {@code count} 份面值 {@code value} 的经验，也就是 {@code value × count}
 * （合并只发生在面值相同、且 id 落在同一组的球之间，见 {@code ExperienceOrb#canMerge}）。
 *
 * <p>Clumps 换成自己那套合并规则后，会把一个球的 {@code value} 直接改成它装着的总量、把 {@code count}
 * 改成合并前的球数；这时总量就是 {@code value} 本身，再乘 {@code count} 会把经验算成好几倍。
 * 所以装了 Clumps 时按 {@code value} 算，没装时按原版的 {@code value × count} 算。
 */
public final class ClumpsCompat {

    /** Clumps 混入经验球的接口，没装 Clumps 时为 {@code null}。 */
    @Nullable
    private static final Class<?> CLUMPED_ORB = findClass("com.blamejared.clumps.helper.IClumpedOrb");

    private ClumpsCompat() {
    }

    /**
     * 该经验球装着的总经验。
     *
     * @param orb 待读取的经验球
     * @return 该球里的经验总量
     */
    public static int totalExperience(ExperienceOrb orb) {
        if (CLUMPED_ORB != null && CLUMPED_ORB.isInstance(orb)) return orb.getValue();

        return orb.getValue() * ((ExperienceOrbAccess) (Object) orb).sculkborne$getCount();
    }

    @Nullable
    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, ClumpsCompat.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
