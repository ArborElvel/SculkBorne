package com.unddefined.sculkborne.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public class ParticleMethods {
    public static void spawnInfrasoundParticles(ClientLevel level, Vec3 center, float radius, boolean isStatic) {
        RandomSource random = level.random;

        // 主要粒子颜色 #111b21 (深蓝绿色)
        final float primaryR = 0x11 / 255.0f;
        final float primaryG = 0x1b / 255.0f;
        final float primaryB = 0x21 / 255.0f;

        // 次要粒子颜色 #0b5464 (深青色)
        final float secondaryR = 0x0b / 255.0f;
        final float secondaryG = 0x54 / 255.0f;
        final float secondaryB = 0x64 / 255.0f;

        // 第三种粒子颜色 #29dfeb (亮青色)
        final float tertiaryR = 0x29 / 255.0f;
        final float tertiaryG = 0xdf / 255.0f;
        final float tertiaryB = 0xeb / 255.0f;

        if (!isStatic) {
            int particleCount = (int) Math.min(Math.PI * radius * radius * 1f, 2400);
            for (int i = 0; i < particleCount; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = random.nextDouble() * radius;

                double startX = center.x + Math.cos(angle) * distance * 0.5;
                double startZ = center.z + Math.sin(angle) * distance * 0.5;

                double endX = center.x + Math.cos(angle) * radius;
                double endZ = center.z + Math.sin(angle) * radius;

                // 根据距离确定粒子类型
                float particleSelector = random.nextFloat();

                if (particleSelector < 0.80) {
                    level.addParticle(new DirectlyMovingDustOptions(80, primaryR, primaryG, primaryB, 1F),
                            startX, center.y, startZ, endX, center.y, endZ);
                } else if (particleSelector < 0.90) {
                    level.addParticle(new DirectlyMovingDustOptions(80, secondaryR, secondaryG, secondaryB, 1F),
                            startX, center.y, startZ, endX, center.y, endZ);
                } else {
                    level.addParticle(new DirectlyMovingDustOptions(80, tertiaryR, tertiaryG, tertiaryB, 1F),
                            startX, center.y, startZ, endX, center.y, endZ);
                }
            }
        } else {
            int particleCount = (int) (Math.PI * radius * radius * 100 + 1);
            // 不规则分布：先随机生成若干“雾团中心”，再让粒子围绕中心聚散，
            // 替代均匀铺满球体，使云团疏密不均、边缘参差
            int clusterCount = Math.max(2, (int) Math.round(Math.cbrt(particleCount)));
            double[] clusterX = new double[clusterCount];
            double[] clusterY = new double[clusterCount];
            double[] clusterZ = new double[clusterCount];
            double[] spreadX = new double[clusterCount];
            double[] spreadY = new double[clusterCount];
            double[] spreadZ = new double[clusterCount];
            for (int c = 0; c < clusterCount; c++) {
                double theta = random.nextDouble() * 2 * Math.PI; // 方位角
                double phi = Math.acos(2 * random.nextDouble() - 1); // 极角
                // 雾团中心散布在球体内部，并保留原有的 +0.4 上移
                double dist = radius * 0.65 * Math.cbrt(random.nextDouble());
                clusterX[c] = center.x + dist * Math.sin(phi) * Math.cos(theta);
                clusterY[c] = center.y + 0.4 + dist * Math.cos(phi);
                clusterZ[c] = center.z + dist * Math.sin(phi) * Math.sin(theta);

                double baseSpread = radius * 0.75;
                // 各雾团三轴扩散幅度不同，形成歪斜、不规则的浓淡分布
                spreadX[c] = baseSpread * (0.6 + 0.8 * random.nextDouble());
                spreadY[c] = baseSpread * (0.6 + 0.8 * random.nextDouble());
                spreadZ[c] = baseSpread * (0.6 + 0.8 * random.nextDouble());
            }

            for (int i = 0; i < particleCount; i++) {
                int cluster = random.nextInt(clusterCount);
                // 两次均匀随机叠加成峰形分布，粒子向所选雾团中心聚拢
                double x = clusterX[cluster] + (random.nextDouble() + random.nextDouble() - 1) * spreadX[cluster];
                double y = clusterY[cluster] + (random.nextDouble() + random.nextDouble() - 1) * spreadY[cluster];
                double z = clusterZ[cluster] + (random.nextDouble() + random.nextDouble() - 1) * spreadZ[cluster];

                // 根据位置确定粒子类型
                float particleSelector = random.nextFloat();

                if (particleSelector < 0.80 - radius) {
                    level.addParticle(new DirectlyMovingDustOptions(20, primaryR, primaryG, primaryB, 0.07F),
                            x, y, z, x, y, z);
                } else if (particleSelector < 0.99 - radius) {
                    level.addParticle(new DirectlyMovingDustOptions(20, secondaryR, secondaryG, secondaryB, 0.07F),
                            x, y, z, x, y, z);
                } else {
                    level.addParticle(new DirectlyMovingDustOptions(20, tertiaryR, tertiaryG, tertiaryB, 0.07F),
                            x, y, z, x, y, z);
                }
            }
        }
    }
}
