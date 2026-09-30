package com.unddefined.sculkborne.client.model.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.unddefined.sculkborne.blocks.entity.SculkHeadBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.util.RenderUtil;

/**
 * 戴上幽匿头颅时用的模型。
 *
 * <p>直接照 {@code geo/block} 里那颗头渲染，几何、UV 都和放下的头一样，所以戴在头上和放在地上长得一样
 * （触须也会一起画出来）。
 *
 * <p>之所以要自己写一个 {@link SkullModelBase}：戴头时原版走 {@code SkullBlockRenderer}，一个模型一次
 * 只绑一张贴图，而幽匿僵尸的触须在放下的头里是用原版感测体的触须贴图单独画的一遍（见
 * {@code SculkZombieHeadTendrilLayer}），一次绑定画不出来。这里改成从方块图集里按 sprite 取贴图——
 * 头取本模组方块贴图那张 sprite、触须骨骼取原版触须的 sprite，两张贴图就能画在同一遍里，也不用把
 * 原版贴图抄进模组。
 */
public class SculkHeadSkullModel extends SkullModelBase {
    /** 幽匿僵尸头的方块贴图，geo 里边就是按它的排布画的。 */
    private static final ResourceLocation ZOMBIE_HEAD_SPRITE =
            ResourceLocation.fromNamespaceAndPath("sculkborne", "block/sculk_zombie_head");
    /** 幽匿骷髅头的贴图本身就把触须画在里面了，不用换第二张。 */
    private static final ResourceLocation SKELETON_HEAD_SPRITE =
            ResourceLocation.fromNamespaceAndPath("sculkborne", "block/sculk_skeleton_head");
    /** 放下的幽匿僵尸头的触须用的是原版感测体的触须贴图，戴在头上也照旧。 */
    private static final ResourceLocation TENDRIL_SPRITE =
            ResourceLocation.withDefaultNamespace("block/sculk_sensor_tendril_inactive");
    /** geo.json 里用触须贴图的那根骨骼。 */
    private static final String TENDRIL_BONE = "tendril";

    private final GeoModel<SculkHeadBlockEntity> model;
    private final ResourceLocation headSprite;
    private final @Nullable ResourceLocation tendrilSprite;
    private float yRot;
    private float xRot;

    private SculkHeadSkullModel(GeoModel<SculkHeadBlockEntity> model, ResourceLocation headSprite,
                                @Nullable ResourceLocation tendrilSprite) {
        this.model = model;
        this.headSprite = headSprite;
        this.tendrilSprite = tendrilSprite;
    }

    public static SculkHeadSkullModel zombie() {
        return new SculkHeadSkullModel(new SculkZombieHeadModel<>(), ZOMBIE_HEAD_SPRITE, TENDRIL_SPRITE);
    }

    public static SculkHeadSkullModel skeleton() {
        return new SculkHeadSkullModel(new SculkSkeletonHeadModel<>(), SKELETON_HEAD_SPRITE, null);
    }

    /** 头没有嘴部动画，只要朝向；戴在头上时原版固定传 180°。 */
    @Override
    public void setupAnim(float mouthAnimation, float yRot, float xRot) {
        this.yRot = yRot;
        this.xRot = xRot;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        BakedGeoModel bakedModel = this.model.getBakedModel(this.model.getModelResource(null, null));
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite head = atlas.getSprite(this.headSprite);
        TextureAtlasSprite tendril = this.tendrilSprite == null ? head : atlas.getSprite(this.tendrilSprite);

        poseStack.pushPose();
        // geo 是 y 向上的方块坐标，原版头颅这套空间是 y 向下、x 也反过来（等于绕 z 转 180°），
        // 先把模型换到头颅的空间里，再按 yRot 转朝向（戴在头上时是 180°）
        poseStack.mulPose(Axis.YP.rotation(Mth.DEG_TO_RAD * this.yRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotation(Mth.DEG_TO_RAD * this.xRot));

        for (GeoBone bone : bakedModel.topLevelBones()) {
            renderBone(poseStack, bone, head, tendril, buffer, packedLight, packedOverlay, colour);
        }

        poseStack.popPose();
    }

    private static void renderBone(PoseStack poseStack, GeoBone bone, TextureAtlasSprite head, TextureAtlasSprite tendril,
                                   VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);

        // 这里不看骨骼的隐藏标记：放下的头那套渲染层会把 tendril 骨骼临时藏起来，而 geo 是全局缓存的同一份，
        // 标记会留着，戴头时再看就会漏画触须
        TextureAtlasSprite sprite = TENDRIL_BONE.equals(bone.getName()) ? tendril : head;
        for (GeoCube cube : bone.getCubes()) {
            renderCube(poseStack, cube, sprite, buffer, packedLight, packedOverlay, colour);
        }

        for (GeoBone child : bone.getChildBones()) {
            renderBone(poseStack, child, head, tendril, buffer, packedLight, packedOverlay, colour);
        }

        poseStack.popPose();
    }

    /** 与 GeckoLib 的 {@code GeoRenderer#renderCube} 同口径，只是 UV 换成图集 sprite 上的坐标。 */
    private static void renderCube(PoseStack poseStack, GeoCube cube, TextureAtlasSprite sprite, VertexConsumer buffer,
                                   int packedLight, int packedOverlay, int colour) {
        poseStack.pushPose();
        RenderUtil.translateToPivotPoint(poseStack, cube);
        RenderUtil.rotateMatrixAroundCube(poseStack, cube);
        RenderUtil.translateAwayFromPivotPoint(poseStack, cube);

        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        Matrix3f normalPose = poseStack.last().normal();

        for (GeoQuad quad : cube.quads()) {
            if (quad == null) continue;

            Vector3f normal = normalPose.transform(new Vector3f(quad.normal()));
            RenderUtil.fixInvertedFlatCube(cube, normal);

            for (GeoVertex vertex : quad.vertices()) {
                Vector3f position = vertex.position();
                Vector4f vector = pose.transform(new Vector4f(position.x(), position.y(), position.z(), 1.0F));
                buffer.addVertex(vector.x(), vector.y(), vector.z(), colour, sprite.getU(vertex.texU()),
                        sprite.getV(vertex.texV()), packedOverlay, packedLight, normal.x(), normal.y(), normal.z());
            }
        }

        poseStack.popPose();
    }
}
