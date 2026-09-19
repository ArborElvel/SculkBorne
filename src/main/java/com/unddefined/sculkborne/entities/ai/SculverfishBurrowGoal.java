package com.unddefined.sculkborne.entities.ai;

import com.unddefined.sculkborne.entities.SculverfishEntity;
import com.unddefined.sculkborne.entities.SculverfishEntity.BurrowState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 幽匿蠹虫的潜伏、钻出和钻回 Goal。
 *
 * <p>该 Goal 在 {@link BurrowState#ACTIVE} 之外占用 {@code MOVE / JUMP / TARGET} 标记，
 * 因此潜伏和钻地期间会压住原版近战、索敌与游荡 Goal；钻出完成后释放标记，
 * 交由 {@link net.minecraft.world.entity.monster.Silverfish} 体系保留的普通战斗 Goal 处理。
 * 在非幽匿方块上触发钻地时，它会用同样的标记接管移动，先走到附近最近的幽匿块再钻进去。
 *
 * <p>地下移动只以 {@link SculverfishEntity#findRelocationTargets} 给出的完整幽匿块为落点，
 * 到达节点后再把该方块设为锚点并刷新限制范围，从而保证它不会长期离开幽匿区域；
 * 落点不要求和上一格相邻，中间隔着石头或空气也能换过去，所以不连续的幽匿区域同样可以移动。
 *
 * <p>接收到振动时（{@link SculverfishEntity#getVibrationSource()} 不为空）不做随机换位，改成沿着同样的
 * 完整幽匿块朝振动源换位，因此追振动时也不会离开幽匿块；追到幽匿块内离振动源最近的一格，或者更近的几格
 * 都被同类占住时清空振动源，结束本次调查。
 */
public class SculverfishBurrowGoal extends Goal {
    /** 两次地下换位之间的间隔范围（tick）。 */
    private static final int MIN_RELOCATE_INTERVAL = 40;
    private static final int MAX_RELOCATE_INTERVAL = 100;

    /** 一次地下随机游走的最大步数（每步的落点见 {@link SculverfishEntity#findRelocationTargets}）。 */
    private static final int MAX_RELOCATE_STEPS = 5;

    /** 地下移动时每 tick 的速度（格）。 */
    private static final double BURROW_MOVE_SPEED = 0.12D;

    /** 钻出/钻入时每 tick 的速度（格）。 */
    private static final double TRANSITION_SPEED = 0.18D;

    /** 钻出/钻入最多持续的时间（tick），防止被卡住。 */
    private static final int MAX_TRANSITION_TICKS = 12;

    /** 撤退钻地（受伤或击中目标）后至少保持潜伏的时间（tick），期间只在地下换位，不会马上钻出。 */
    private static final int RETREAT_HIDE_TICKS = 60;

    /** 钻出/钻入一次的方块碎屑粒子数量与水平散布（格），对齐原版监守者挖地的表现。 */
    private static final int DIG_PARTICLE_COUNT = 10;
    private static final double DIG_PARTICLE_SPREAD = 0.3D;

    /** 侧面碎屑：每个面的粒子数量，以及为了让碎屑露在方块外而沿面法线往外偏的距离（格）。 */
    private static final int DIG_SIDE_PARTICLE_COUNT = 6;
    private static final double DIG_SIDE_OFFSET = 0.02D;

    /**
     * 在方块里移动时漏出碎屑的概率（每刻 1/N）、每次每个面的粒子数量与散布（格）。
     *
     * <p>数量刻意压得很低：它是给玩家“幽匿在动”的提示，而不是把它的位置直接标出来。
     */
    private static final int MOVE_PARTICLE_CHANCE = 4;
    private static final int MOVE_PARTICLE_COUNT = 1;
    private static final double MOVE_PARTICLE_SPREAD = 0.25D;

    /**
     * 钻入允许的最大位移（格）的平方。
     *
     * <p>本地候选的最坏几何是「斜下方一格 + 站在自己格子的最远端」：1.0 宽的碰撞箱下约 1.75 格，
     * 所以取 2.0 格，既覆盖全部“脚边”目标，又小于 {@value #MAX_TRANSITION_TICKS} tick 过渡能走完的
     * {@code 12 × 0.18 = 2.16} 格，末尾的兜底 {@code moveTo} 不会造成可见位移。
     */
    private static final double MAX_TRANSITION_DISTANCE_SQR = 4.0D;

    /** 暴露在外（地面活动）时，向外搜索可钻幽匿块的最大水平/竖直半径（格）。 */
    private static final int ANCHOR_SEARCH_RADIUS = 16;
    private static final int ANCHOR_SEARCH_HEIGHT = 8;

    /** 走过去钻时的移动速度，以及最长接近时间（tick）；超时或走不到就放弃接近。 */
    private static final double APPROACH_SPEED = 1.0D;
    private static final int MAX_APPROACH_TICKS = 200;

    /** 接近途中的重新寻路间隔（tick）与连续寻路失败多少次后放弃。 */
    private static final int MIN_REPATH_INTERVAL = 10;
    private static final int MAX_REPATH_INTERVAL = 20;
    private static final int MAX_REPATH_FAILURES = 3;

    /** 接近幽匿块失败（附近没有、或走不到）后的等待时间（tick），避免原地反复起停。 */
    private static final int BURROW_RETRY_DELAY_TICKS = 100;

    /** 寻路停下后还允许直接朝目标挪的最大距离（格）的平方。 */
    private static final double STEER_DISTANCE_SQR = 9.0D;

    /** 直接朝目标挪动时每 tick 位移（格）。 */
    private static final double STEER_SPEED = 0.2D;

    private final SculverfishEntity mob;
    private final List<BlockPos> relocatePath = new ArrayList<>();

    private int relocateCooldown;
    private int pathIndex;
    private int transitionTicks;
    private int hideTicks;

    /** 本次钻地是不是撤退（受伤或击中目标）；撤退钻地在钻到底后要先躲一段时间。 */
    private boolean retreatDive;

    /** 附近找到的幽匿块；不为空时处于地面接近阶段，走到它旁边再钻进去。 */
    @Nullable
    private BlockPos approachTarget;

    /** 接近阶段准备钻进去的那块幽匿块；走过去时一直盯着它。 */
    @Nullable
    private BlockPos approachBlock;

    private int approachTicks;
    private int repathCooldown;
    private int repathFailures;

    @Nullable
    private BlockPos transitionTarget;
    @Nullable
    private Player emergeTarget;

    public SculverfishBurrowGoal(SculverfishEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!mob.isAlive()) return false;
        if (mob.getBurrowState() == BurrowState.ACTIVE) {
            // 受伤、击中目标、目标丢失或地面停留超时后，主动接管战斗 Goal 开始钻地
            return mob.shouldBurrow();
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!mob.isAlive()) return false;
        // 地面接近阶段也归这个 Goal 管：回到幽匿块钻进去（或放弃）之前不交还控制权
        return mob.getBurrowState() != BurrowState.ACTIVE || approachTarget != null;
    }

    @Override
    public void start() {
        if (mob.getBurrowState() == BurrowState.ACTIVE) {
            // 撤退钻地的请求要在 refreshBurrowState 清掉它之前取出来
            this.retreatDive = mob.consumeBurrowRequest();
        }
        mob.refreshBurrowState();
        mob.getNavigation().stop();

        if (mob.getBurrowState() == BurrowState.ACTIVE) {
            if (findBurrowDestination() != null) mob.setBurrowState(BurrowState.BURROWING);
            else if (mob.isBurrowReady()) {
                // 脚下/紧邻没有幽匿块：先在附近搜一个能走过去的，走过去再钻
                this.approachTarget = findApproachTarget();
                this.approachTicks = 0;
                this.repathCooldown = 0;
                this.repathFailures = 0;
                // 附近 16 × 8 格内都没有可钻的幽匿块，就别每 20 tick 再扫一遍了
                if (this.approachTarget == null) mob.delayBurrow(BURROW_RETRY_DELAY_TICKS);
            }
        }

        switch (mob.getBurrowState()) {
            case BURROWED -> {
                relocatePath.clear();
                pathIndex = 0;
                relocateCooldown = nextRelocateCooldown();
            }
            case BURROWING -> {
                transitionTicks = 0;
                transitionTarget = findBurrowDestination();
            }
            case EMERGING -> {
                transitionTicks = 0;
                transitionTarget = mob.getBurrowAnchor();
            }
            case ACTIVE -> {
            }
        }
    }

    @Override
    public void tick() {
        switch (mob.getBurrowState()) {
            case BURROWED -> tickBurrowed();
            case EMERGING -> tickEmerging();
            case BURROWING -> tickBurrowing();
            case ACTIVE -> tickApproaching();
        }
    }

    @Override
    public void stop() {
        relocatePath.clear();
        pathIndex = 0;
        transitionTarget = null;
        emergeTarget = null;
        approachTarget = null;
        approachBlock = null;
        approachTicks = 0;
        repathCooldown = 0;
        repathFailures = 0;
        retreatDive = false;
        hideTicks = 0;
        mob.getNavigation().stop();
    }

    private void tickBurrowed() {
        // 潜伏期间不接受爆炸/水流等外部位移，始终停留在锚点方块内部
        mob.setDeltaMovement(Vec3.ZERO);

        BlockPos anchor = mob.getBurrowAnchor();
        if (anchor == null || !SculverfishEntity.isFullSculkBlock(mob.level(), anchor)) {
            // 类似被挖掉的虫蚀方块：优先当场钻出暴露自己，出口被堵死时才换到紧邻的一格
            if (tryEmergeFromCurrentBlock()) return;

            BlockPos replacement = findBurrowDestination();
            if (replacement != null) {
                mob.setBurrowAnchor(replacement);
                beginBurrowing(replacement);
            } else if (!isEmbedded()) {
                // 潜伏点消失且附近没有幽匿块时只能回到地面，避免卡在非幽匿方块里
                mob.setBurrowState(BurrowState.ACTIVE);
            }
            // 身体还埋在方块里时不能切回 ACTIVE：出不去就会开始吃窒息伤害，先留在方块里等机会
            return;
        }

        if (mob.consumeBurrowRequest()) {
            // 撤退（受伤或击中目标）：先在地下换位并躲一段时间，避免钻回去又被同一个目标打出来
            hideTicks = RETREAT_HIDE_TICKS;
            relocateCooldown = 0;
        }

        if (hideTicks > 0) hideTicks--;
        else {
            Player player = mob.findNearbyPlayer();
            if (player != null) {
                Vec3 emergePos = mob.findEmergePos(anchor);
                if (emergePos != null && mob.canSeeFrom(emergePos, player)) {
                    beginEmerging(player);
                    return;
                }
                // 附近有玩家但上下左右都钻不出去：优先换一个能钻出的位置
                if (emergePos == null) relocateCooldown = 0;
            }
        }

        if (!relocatePath.isEmpty()) {
            followRelocatePath();
            return;
        }

        // 接收到振动时不受随机换位冷却的限制，立刻沿着完整幽匿块朝振动源换位；撤退躲藏期间不追
        if (hideTicks <= 0 && mob.getVibrationSource() != null) {
            approachVibrationSource();
            return;
        }

        if (--relocateCooldown <= 0 && !buildRelocatePath(anchor)) relocateCooldown = 20;

    }

    /**
     * 朝振动源换位一步：换位方向由 {@link SculverfishEntity#findSculkStepToward} 给出，只经过完整幽匿块，
     * 所以不会离开幽匿块。
     *
     * <p>没有可以再靠近的幽匿块时结束本次调查：清空振动源，{@link InvestigateVibrationGoal} 随之结束，
     * 之后回到随机换位。
     */
    private void approachVibrationSource() {
        BlockPos source = mob.getVibrationSource();
        BlockPos step = source != null ? mob.findSculkStepToward(source) : null;
        if (step == null) {
            mob.clearVibrationSource();
            return;
        }

        Vec3 target = SculverfishEntity.burrowPos(step);
        if (mob.position().distanceToSqr(target) < 0.04D) mob.setBurrowAnchor(step);
        else moveTowards(target, BURROW_MOVE_SPEED);
    }

    private void tickEmerging() {
        BlockPos anchor = transitionTarget != null ? transitionTarget : mob.getBurrowAnchor();
        Vec3 target = anchor != null ? mob.findEmergePos(anchor) : null;
        if (target == null) {
            // 钻出过程中出口被堵住，退回潜伏状态
            mob.setBurrowState(BurrowState.BURROWED);
            return;
        }

        transitionTicks++;
        if (transitionTicks >= MAX_TRANSITION_TICKS || mob.position().distanceToSqr(target) < 0.01D) {
            mob.moveTo(target.x, target.y, target.z, mob.getYRot(), mob.getXRot());
            mob.setBurrowState(BurrowState.ACTIVE);
            if (emergeTarget != null && emergeTarget.isAlive()) mob.setTarget(emergeTarget);

            return;
        }

        moveTowards(target, TRANSITION_SPEED);
    }

    private void tickBurrowing() {
        if (!isValidBurrowTarget(transitionTarget)) {
            transitionTarget = findBurrowDestination();
            transitionTicks = 0;
            if (!isValidBurrowTarget(transitionTarget)) {
                transitionTarget = null;
                mob.delayBurrow(BURROW_RETRY_DELAY_TICKS);
                // 中止时如果身体已经进到方块里，不能直接切回 ACTIVE：那会开始吃窒息伤害。
                // 先就地钻出去（成功时会自己切回 ACTIVE），钻不出去就先留在方块里（noPhysics 不会窒息）。
                if (!isEmbedded()) mob.setBurrowState(BurrowState.ACTIVE);
                else if (!tryEmergeFromCurrentBlock()) mob.setBurrowState(BurrowState.BURROWED);

                return;
            }
        }

        transitionTicks++;
        Vec3 target = SculverfishEntity.burrowPos(transitionTarget);
        if (transitionTicks >= MAX_TRANSITION_TICKS || mob.position().distanceToSqr(target) < 0.01D) {
            mob.moveTo(target.x, target.y, target.z, mob.getYRot(), mob.getXRot());
            mob.setBurrowAnchor(transitionTarget);
            mob.setBurrowState(BurrowState.BURROWED);
            playBurrowEffects(transitionTarget, false);
            relocatePath.clear();
            pathIndex = 0;
            relocateCooldown = nextRelocateCooldown();
            if (retreatDive) {
                // 撤退钻地：先在地下躲一段时间并换位，再考虑钻出
                retreatDive = false;
                hideTicks = RETREAT_HIDE_TICKS;
                relocateCooldown = 0;
            }
            return;
        }

        moveTowards(target, TRANSITION_SPEED);
    }

    /**
     * 地面接近阶段：用寻路走到附近找到的那格幽匿块上面，路上踩到幽匿块就提前钻进去。
     *
     * <p>钻入本身仍然是原地短过渡，这里刻意让它自己走过去，避免出现“远程钻进方块”。
     */
    private void tickApproaching() {
        BlockPos target = approachTarget;
        if (target == null) return;

        BlockPos destination = findBurrowDestination();
        if (destination != null) {
            approachTarget = null;
            beginBurrowing(destination);
            return;
        }

        if (++approachTicks > MAX_APPROACH_TICKS) {
            cancelApproach();
            return;
        }

        Vec3 walkTarget = Vec3.atBottomCenterOf(target);

        // 寻路在离目标 1 格以内就算“到了”，而且 1.0 宽的碰撞箱会让它要求目标格两侧各留一格，
        // 贴着幽匿块（尤其是墙面）的落脚点很可能根本找不到路。这两种情况都会让它停在目标一两格之外
        // 反复重寻路却不钻——目标已经不远时就直接朝它挪过去，靠自己走到钻入范围里。
        if (mob.getNavigation().isDone() && mob.position().distanceToSqr(walkTarget) <= STEER_DISTANCE_SQR) {
            // 被一格的台阶挡住就跳一下，否则只会在台阶前一直顶着
            if (mob.horizontalCollision && mob.onGround()) mob.getJumpControl().jump();
            moveTowards(walkTarget, STEER_SPEED);
        } else if (--repathCooldown <= 0) {
            repathCooldown = MIN_REPATH_INTERVAL + mob.getRandom().nextInt(MAX_REPATH_INTERVAL - MIN_REPATH_INTERVAL + 1);
            if (mob.getNavigation().moveTo(walkTarget.x, walkTarget.y, walkTarget.z, APPROACH_SPEED)) {
                repathFailures = 0;
            } else if (++repathFailures >= MAX_REPATH_FAILURES) {
                // 走不到（被挡住、目标在限制范围外等）：放弃接近，把控制权还给战斗 Goal
                cancelApproach();
                return;
            }
        }

        // 一直盯着要钻进去的那块幽匿块（moveTowards 设的是落脚点，这里覆盖回目标方块）
        mob.getLookControl().setLookAt(approachBlock != null ? Vec3.atCenterOf(approachBlock) : walkTarget);
    }

    /** 放弃接近并停下导航。 */
    private void cancelApproach() {
        approachTarget = null;
        approachBlock = null;
        mob.getNavigation().stop();
        mob.delayBurrow(BURROW_RETRY_DELAY_TICKS);
    }

    /** 开始钻出：记录目标并播放钻出表现。 */
    private void beginEmerging(Player player) {
        BlockPos anchor = mob.getBurrowAnchor();
        if (anchor == null) return;

        this.emergeTarget = player;
        this.transitionTarget = anchor;
        this.transitionTicks = 0;
        this.relocatePath.clear();
        this.pathIndex = 0;
        mob.setBurrowState(BurrowState.EMERGING);
        playBurrowEffects(anchor, true);
    }

    /** 开始钻入指定幽匿块。 */
    private void beginBurrowing(BlockPos target) {
        this.transitionTarget = target;
        this.transitionTicks = 0;
        this.relocatePath.clear();
        this.pathIndex = 0;
        mob.setBurrowState(BurrowState.BURROWING);
    }

    /**
     * 只能钻入自身所在、或紧邻（脚下、同级相邻、斜下方、正上方）的一格完整幽匿块。
     *
     * <p>它可以在垂直方向的幽匿块里上下移动，也能从侧面钻进贴着的幽匿块。
     *
     * <p>所有候选都要通过 {@link #isValidBurrowTarget} 才算数：取目标和校验目标必须用同一套判据，
     * 否则会出现「取到了目标、过渡却判定太远而中止」的反复——那正是站在幽匿块旁边却原地抖动的原因。
     * 更远的目标不属于“脚边”，交给地面接近阶段走过去再钻。
     */
    @Nullable
    private BlockPos findBurrowDestination() {
        BlockPos current = mob.blockPosition();
        if (isValidBurrowTarget(current)) return current;

        BlockPos below = current.below();
        if (isValidBurrowTarget(below)) return below;

        BlockPos above = current.above();
        if (isValidBurrowTarget(above)) return above;

        // 同级相邻的一格，以及刚走下来那一层（斜下方一格，即脚边地面的幽匿块）
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = current.relative(direction);
            if (isValidBurrowTarget(side)) return side;

            BlockPos sideBelow = side.below();
            if (isValidBurrowTarget(sideBelow)) return sideBelow;
        }

        return null;
    }

    /**
     * 钻地目标是否可钻：存在、是完整幽匿块、就在脚下或紧邻（不超过 {@value #MAX_TRANSITION_DISTANCE_SQR}
     * 的平方根），并且这一格里还没有挤满同类（见 {@link SculverfishEntity#MAX_PER_BLOCK}）。
     */
    private boolean isValidBurrowTarget(@Nullable BlockPos target) {
        if (target == null || !SculverfishEntity.isFullSculkBlock(mob.level(), target)) return false;
        if (mob.position().distanceToSqr(SculverfishEntity.burrowPos(target)) > MAX_TRANSITION_DISTANCE_SQR) return false;

        return !SculverfishEntity.isBurrowBlockFull(mob.level(), target, mob);
    }

    /** 这一格能不能钻：完整幽匿块，而且还没有挤满同类。 */
    private boolean isDiggableSculk(BlockPos pos) {
        return SculverfishEntity.isFullSculkBlock(mob.level(), pos)
                && !SculverfishEntity.isBurrowBlockFull(mob.level(), pos, mob);
    }

    /** 身体是不是正卡在方块里（碰撞箱与方块重叠）。切回 ACTIVE 之前必须确认它是 false，否则会开始窒息。 */
    private boolean isEmbedded() {
        return !mob.level().noBlockCollision(mob, mob.getBoundingBox());
    }

    /**
     * 在水平 {@value #ANCHOR_SEARCH_RADIUS} 格、竖直 {@value #ANCHOR_SEARCH_HEIGHT} 格内
     * 找最近的、能钻进去的幽匿块旁的落脚点，作为地面接近阶段的目标。
     *
     * <p>从近到远逐层向外搜索，某一层里找到就直接返回，因此范围开得大也不会每次都扫满全盒。
     * 只用于“走过去再钻”：找到的方块不会直接成为钻入目标，钻入永远发生在脚边或紧邻一格。
     */
    @Nullable
    private BlockPos findApproachTarget() {
        BlockPos origin = mob.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int radius = 1; radius <= ANCHOR_SEARCH_RADIUS; radius++) {
            int verticalLimit = Math.min(radius, ANCHOR_SEARCH_HEIGHT);
            BlockPos best = null;
            BlockPos bestBlock = null;
            double bestDistance = Double.MAX_VALUE;

            for (int y = -verticalLimit; y <= verticalLimit; y++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        // 只看这一层的壳，里层已经确认没有可钻的幽匿块了
                        if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;

                        cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        BlockPos sculk = findAdjacentSculkBlock(cursor);
                        if (sculk == null) continue;

                        double distance = origin.distSqr(cursor);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = cursor.immutable();
                            bestBlock = sculk.immutable();
                        }
                    }
                }
            }

            if (best != null) {
                approachBlock = bestBlock;
                return best;
            }
        }

        return null;
    }

    /**
     * 这一格能不能作为接近幽匿块的落脚点；能的话返回它准备钻进去的那块幽匿块。
     *
     * <p>判据与钻出、钻入保持一致：本身不能是实心方块（容得下它），下面必须是实心方块
     * （钻出来有地方站），上方可以不是；站上去之后，脚下、正上方、四邻或斜下方任意一处
     * 是完整幽匿块就算能钻进去。返回的方块同时用来让它在走过去时一直盯着目标。
     */
    @Nullable
    private BlockPos findAdjacentSculkBlock(BlockPos pos) {
        if (SculverfishEntity.isSolidBlock(mob.level(), pos)) return null;
        if (!SculverfishEntity.isSolidBlock(mob.level(), pos.below())) return null;
        if (isDiggableSculk(pos.below())) return pos.below();
        if (isDiggableSculk(pos.above())) return pos.above();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (isDiggableSculk(side)) return side;
            if (isDiggableSculk(side.below())) return side.below();
        }

        return null;
    }

    /** 潜伏方块被破坏/替换时，如果上方有空间就当场钻出。 */
    private boolean tryEmergeFromCurrentBlock() {
        BlockPos pos = mob.blockPosition();
        Vec3 surface = new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
        AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(surface);
        if (!mob.level().noBlockCollision(mob, box)) return false;

        mob.moveTo(surface.x, surface.y, surface.z, mob.getYRot(), mob.getXRot());
        mob.setBurrowState(BurrowState.ACTIVE);
        playBurrowEffects(pos, true);
        return true;
    }

    /** 生成一条只经过完整幽匿方块的随机游走路径，并优先选择能钻出的终点。 */
    private boolean buildRelocatePath(BlockPos start) {
        relocatePath.clear();
        pathIndex = 0;

        List<BlockPos> fallback = List.of();
        for (int attempt = 0; attempt < 4; attempt++) {
            List<BlockPos> candidate = randomSculkPath(start,
                    2 + mob.getRandom().nextInt(MAX_RELOCATE_STEPS - 1));
            if (candidate.isEmpty()) continue;

            if (mob.findEmergePos(candidate.get(candidate.size() - 1)) != null) {
                relocatePath.addAll(candidate);
                return true;
            }

            if (candidate.size() > fallback.size()) fallback = new ArrayList<>(candidate);
        }

        relocatePath.addAll(fallback);
        return !relocatePath.isEmpty();
    }

    private List<BlockPos> randomSculkPath(BlockPos start, int steps) {
        List<BlockPos> path = new ArrayList<>(steps);
        BlockPos current = start;
        BlockPos previous = null;

        for (int i = 0; i < steps; i++) {
            // 候选可以不相邻（见 SculverfishEntity#findRelocationTargets）：幽匿区域不连续时，
            // 隔着石头或空气的另一格幽匿块也能作为换位落点
            List<BlockPos> neighbors = new ArrayList<>(mob.findRelocationTargets(current));
            if (previous != null) neighbors.remove(previous);

            if (neighbors.isEmpty()) break;

            BlockPos next = neighbors.get(mob.getRandom().nextInt(neighbors.size()));
            path.add(next);
            previous = current;
            current = next;
        }

        return path;
    }

    private void followRelocatePath() {
        if (pathIndex < 0 || pathIndex >= relocatePath.size()) {
            relocatePath.clear();
            pathIndex = 0;
            return;
        }

        BlockPos node = relocatePath.get(pathIndex);
        // 途中被别的同类占了就放弃这条路径，另找换位目标
        if (!isDiggableSculk(node)) {
            relocatePath.clear();
            pathIndex = 0;
            relocateCooldown = 20;
            return;
        }

        Vec3 target = SculverfishEntity.burrowPos(node);
        if (mob.position().distanceToSqr(target) < 0.04D) {
            mob.setBurrowAnchor(node);
            pathIndex++;
            if (pathIndex >= relocatePath.size()) {
                relocatePath.clear();
                pathIndex = 0;
                relocateCooldown = nextRelocateCooldown();
            }
            return;
        }

        moveTowards(target, BURROW_MOVE_SPEED);
    }

    private void moveTowards(Vec3 target, double speed) {
        Vec3 delta = target.subtract(mob.position());
        if (delta.lengthSqr() < 1.0E-6D) return;

        faceTravelDirection(delta);
        mob.move(MoverType.SELF, delta.normalize().scale(speed));
        mob.setDeltaMovement(Vec3.ZERO);

        // 地面活动（走过去钻）是走路，不是钻地，不冒碎屑
        if (mob.getBurrowState() != BurrowState.ACTIVE) playMovingParticles();
    }

    /**
     * 按水平行进方向转身，并把头摆到身体正前方、俯仰归零。
     *
     * <p>这里不把目标交给 LookControl：在方块里换位时目标经常在正上或正下（水平偏移为 0），
     * 那样算出来的朝向是任意的，而且只会把头扭过去、身体不动——CEM 公式按「头相对身体的偏航」
     * 摆动整条身体，看起来就是身体歪的。所以改成让身体朝着行进方向转（每刻最多 90°，与原版
     * MoveControl 一致），头与身体同向、俯仰归零。
     *
     * <p>正上/正下移动没有水平分量，这时保持原朝向，只把头摆正。
     */
    private void faceTravelDirection(Vec3 delta) {
        if (delta.x * delta.x + delta.z * delta.z > 1.0E-6D) {
            float yaw = (float) (Mth.atan2(delta.z, delta.x) * 180.0D / Math.PI) - 90.0F;
            mob.setYRot(Mth.approachDegrees(mob.getYRot(), yaw, 90.0F));
        }

        mob.setYHeadRot(mob.getYRot());
        mob.setXRot(0.0F);
    }

    private int nextRelocateCooldown() {
        return MIN_RELOCATE_INTERVAL + mob.getRandom().nextInt(MAX_RELOCATE_INTERVAL - MIN_RELOCATE_INTERVAL + 1);
    }

    /** 钻出/钻入时播放被挖开方块的碎屑粒子与幽匿音效。 */
    private void playBurrowEffects(BlockPos pos, boolean emerging) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) return;

        // 与原版监守者一致：用被挖开方块的碎屑粒子
        BlockState state = serverLevel.getBlockState(pos);
        if (state.getRenderShape() != RenderShape.INVISIBLE) {
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, state);

            // 顶面：它钻入/钻出的位置
            serverLevel.sendParticles(debris,
                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    DIG_PARTICLE_COUNT, DIG_PARTICLE_SPREAD, 0.0D, DIG_PARTICLE_SPREAD, 0.0D);

            // 四个侧面：碎屑贴在面外侧、沿该面所在平面散开，让侧面也看得到方块剥落
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                double x = pos.getX() + 0.5D + direction.getStepX() * (0.5D + DIG_SIDE_OFFSET);
                double z = pos.getZ() + 0.5D + direction.getStepZ() * (0.5D + DIG_SIDE_OFFSET);
                serverLevel.sendParticles(debris, x, pos.getY() + 0.5D, z, DIG_SIDE_PARTICLE_COUNT,
                        direction.getStepX() == 0 ? DIG_PARTICLE_SPREAD : 0.0D, DIG_PARTICLE_SPREAD,
                        direction.getStepZ() == 0 ? DIG_PARTICLE_SPREAD : 0.0D, 0.0D);
            }
        }

        serverLevel.playSound(null, pos,
                emerging ? SoundEvents.SCULK_BLOCK_BREAK : SoundEvents.SCULK_BLOCK_PLACE,
                SoundSource.BLOCKS, 0.45F, 0.8F + mob.getRandom().nextFloat() * 0.4F);
    }

    /**
     * 在方块里换位时偶尔从方块表面漏出一点碎屑，让玩家看得出地下有东西在动。
     *
     * <p>它整只都在方块里，所以碎屑要落在方块表面才看得见（方块内部的粒子会被方块挡住）：
     * 每次在顶面与随机两个侧面各放 {@value #MOVE_PARTICLE_COUNT} 粒，且每刻只有
     * 1/{@value #MOVE_PARTICLE_CHANCE} 的概率触发，效果是零星的剥落而不是一路喷粒子。
     * 当前方块没有可见模型（例如它正好跨在空气里）时什么也不放。
     */
    private void playMovingParticles() {
        if (!(mob.level() instanceof ServerLevel serverLevel)) return;
        if (mob.getRandom().nextInt(MOVE_PARTICLE_CHANCE) != 0) return;

        BlockPos pos = mob.blockPosition();
        BlockState state = serverLevel.getBlockState(pos);
        if (state.getRenderShape() == RenderShape.INVISIBLE) return;

        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, state);

        // 顶面：碎屑从它上方那一面冒出来
        serverLevel.sendParticles(debris,
                pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                MOVE_PARTICLE_COUNT, MOVE_PARTICLE_SPREAD, 0.0D, MOVE_PARTICLE_SPREAD, 0.0D);

        // 随机两个不同的侧面：碎屑贴在面外侧，和钻出/钻入的表现保持同一套位置
        Direction first = Direction.Plane.HORIZONTAL.getRandomDirection(mob.getRandom());
        Direction second = Direction.Plane.HORIZONTAL.getRandomDirection(mob.getRandom());
        while (second == first) second = Direction.Plane.HORIZONTAL.getRandomDirection(mob.getRandom());

        spawnSideDebris(serverLevel, debris, pos, first);
        spawnSideDebris(serverLevel, debris, pos, second);
    }

    /** 在指定侧面的外侧放一份碎屑。 */
    private void spawnSideDebris(ServerLevel level, BlockParticleOption debris, BlockPos pos, Direction side) {
        level.sendParticles(debris,
                pos.getX() + 0.5D + side.getStepX() * (0.5D + DIG_SIDE_OFFSET),
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D + side.getStepZ() * (0.5D + DIG_SIDE_OFFSET),
                MOVE_PARTICLE_COUNT,
                side.getStepX() == 0 ? MOVE_PARTICLE_SPREAD : 0.0D, MOVE_PARTICLE_SPREAD,
                side.getStepZ() == 0 ? MOVE_PARTICLE_SPREAD : 0.0D, 0.0D);
    }
}
