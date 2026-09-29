package com.unddefined.sculkborne.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.unddefined.sculkborne.entities.WandererEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
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
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.FastBoneFilterGeoLayer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 徘徊者眼睛的自发光层：眼窝是纯白、瞳孔是贴图里的异瞳色，两者都不吃光照、也都<b>只画这一遍</b>。
 *
 * <p>眼睛在 geo.json 里是真几何：{@code *_eyelid} 上带着眼窝方块（它的内壁就是眼白），
 * {@code *_eye_pupil_up} 上带着瞳孔方块。这一层负责：
 *
 * <ul>
 *   <li>眼窝：{@link #SOCKET_RENDER_TYPE} —— 采样原版纯白贴图 {@code textures/misc/white.png}
 *       再乘 {@link #SOCKET_COLOUR}。那张图 4×4 全白全不透明，所以<b>眼窝和自己的 uv 无关</b>，
 *       永远是纯白；想调亮度/色偏只改这个常量。</li>
 *   <li>瞳孔：主贴图 + {@code entityTranslucentEmissive}，颜色就是贴图上那两格像素
 *       （geo 的 {@code left_eye} = 实体右眼 {@code CC00FA}、{@code right_eye} = 左眼 {@code #29DFEB}）。</li>
 * </ul>
 *
 * <p><b>为什么要先在主贴图那一遍把它们藏掉</b>：主贴图那一遍用 {@code entityTranslucent}
 * （lightmap × 假光照），眼窝贴图的白色像素会被压到一半上下（白天约 {@code #8B8B8B}）。
 * 同一个方块被画两遍时两遍不完全重合，眼白就会一半是这一层的亮白、另一半是主贴图那遍的灰白，
 * 所以眼窝和瞳孔都交给这一层独占。父类 {@link FastBoneFilterGeoLayer}
 * 负责把它们在主贴图那一遍藏起来（写法与 {@code SculkZombieTendrilLayer} 一致）。
 *
 * <p><b>显式写顶点的原因（"侧面看明暗不一样"的真正来源）</b>：{@code entityTranslucentEmissive}
 * 用的 {@code rendertype_entity_translucent_emissive} 顶点着色器里是
 * {@code vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color)} ——
 * 它按<b>面的法线</b>做一次假光照，根本不管光照贴图。于是同一个眼窝方块的每个面亮度都不同
 * （+y 面 1.00、±z 面 0.74、±x 面 0.50、-y 面 0.40），怎么换贴图、怎么加自发光都消不掉，
 * 视角一转就能看到哪个面亮哪个面暗。所以这里不再用 {@code GeoRenderer#renderCube}，
 * 而是自己写顶点、把法线统一成 (0,1,0)（假光照里最亮的方向），
 * 于是所有面都输出满亮度；几何位置仍按方块自己的 pivot / 旋转算，眼窝的立体形状不变。
 *
 * <p><b>为什么不自己写 RenderType 来"无贴图绘制"</b>：{@code GeoRenderer.renderCube} 按
 * {@link com.mojang.blaze3d.vertex.DefaultVertexFormat#NEW_ENTITY} 写顶点（位置、颜色、uv、overlay、
 * lightmap、法线），所以缓冲区的顶点格式必须是 NEW_ENTITY。自建一个 POSITION_COLOR 格式的 RenderType
 * 会把写进去的数据按完全不同的步长解读，顶点错位 → 每个面颜色/位置都不一样。
 * 想画纯色，正确做法就是像这里一样：NEW_ENTITY 格式的渲染类型 + 一张全白贴图 + 顶点色。
 *
 * <p>用自发光（{@link RenderType#entityTranslucentEmissive}）的意义和原版 {@code EnderEyesLayer} 一样：
 * 末影人的眼睛不吃光照，在黑暗里照样是亮的。徘徊者是幽匿生物、基本只在暗处活动，
 * 只走主贴图那一遍的话眼白和瞳孔都会被 lightmap 压暗一大截。
 * 眼皮片 {@code *_blink} 不在这里，它跟着主贴图那一遍走（和 FA 里眼皮不吃发光层一致）。
 *
 * <p>绘制放在 {@link #render}：1.21 的 {@code MultiBufferSource} 按 {@link RenderType} 复用共享批次，
 * 在模型遍历中途换渲染类型会把已经攒下的主模型批次提前结束掉，之后渲染的骨骼拿到的就是已经 build 过的
 * 旧 buffer，实体会走 outline 通道发光时排在后边的骨骼会直接画不出来（原因见
 * {@code SculkZombieTendrilLayer} 的注释）。所以 {@link #renderForBone} 只记下模型遍历到这些骨骼时的姿势
 * （那时的 poseStack 已经处在骨骼坐标系里），等整只模型遍历完再按记下的姿势把它们画出来。
 */
public class WandererEyesLayer extends FastBoneFilterGeoLayer<WandererEntity> {
    /** 带着眼窝方块的骨骼：纯白绘制，不取主贴图。 */
    private static final List<String> SOCKET_BONES = List.of("left_eyelid", "right_eyelid");

    /** 带着瞳孔方块的骨骼：走主贴图，取贴图里的异瞳颜色。 */
    private static final List<String> PUPIL_BONES = List.of("l_eye_pupil_up", "r_eye_pupil_up");

    /** 主贴图那一遍要藏掉的骨骼：眼窝和瞳孔都由这一层独占绘制。 */
    private static final List<String> EYE_BONES = List.of(
            "left_eyelid", "right_eyelid", "l_eye_pupil_up", "r_eye_pupil_up");

    /** 原版纯白贴图（4×4 全白全不透明），眼窝采样它，因此和自己的 uv 无关。 */
    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** 眼窝的渲染类型：纯白贴图 + 自发光；不吃光照贴图，剩下的假光照由 (0,1,0) 抹平。 */
    private static final RenderType SOCKET_RENDER_TYPE = RenderType.entityTranslucentEmissive(WHITE_TEXTURE);

    /** 眼窝的颜色，纯白；想让它暗一点/带色偏就改这里（不用动贴图）。 */
    private static final int SOCKET_COLOUR = 0xFFFFFFFF;

    /** 自发光不参与受伤闪红，瞳孔固定用不透明白色，贴图颜色原样输出。 */
    private static final int PUPIL_COLOUR = 0xFFFFFFFF;

    /** 需要重画的骨骼在这一帧的姿势，由 {@link #renderForBone} 记下、{@link #render} 使用。 */
    private final Map<String, Pose> poses = new LinkedHashMap<>();

    /** 一根骨骼这一帧的模型空间姿势。 */
    private record Pose(GeoBone bone, Matrix4f pose, Matrix3f normal) {
    }

    public WandererEyesLayer(GeoRenderer<WandererEntity> renderer) {
        // 主贴图那一遍把眼睛骨骼藏掉：眼窝和瞳孔都只由这一层画，避免同一个面被两遍不同亮度覆盖
        super(renderer, () -> EYE_BONES, (bone, animatable, partialTick) -> {
            bone.setHidden(true);
            // setHidden 会连带隐藏子骨骼，这里放开子级：*_blink 眼皮还要跟着主贴图走
            bone.setChildrenHidden(false);
        });
    }

    @Override
    public void preRender(PoseStack poseStack, WandererEntity animatable, BakedGeoModel bakedModel,
                          @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          float partialTick, int packedLight, int packedOverlay) {
        // 父类负责把眼睛骨骼在主贴图那一遍藏掉，这里只是清掉上一帧的姿势
        super.preRender(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);

        this.poses.clear();
    }

    /**
     * 模型遍历到眼部骨骼时只记下它的姿势，绘制交给 {@link #render}，见类注释。
     */
    @Override
    public void renderForBone(PoseStack poseStack, WandererEntity animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay) {
        if (!SOCKET_BONES.contains(bone.getName()) && !PUPIL_BONES.contains(bone.getName())) return;

        this.poses.put(bone.getName(),
                new Pose(bone, new Matrix4f(poseStack.last().pose()), new Matrix3f(poseStack.last().normal())));
    }

    /**
     * 整只模型画完之后再画眼睛：此时换渲染类型不会影响任何骨骼。
     */
    @Override
    public void render(PoseStack poseStack, WandererEntity animatable, BakedGeoModel bakedModel,
                       @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        Map<String, Pose> eyes = new LinkedHashMap<>(this.poses);
        this.poses.clear();

        // 隐身时不画眼睛，与原版 EnderEyesLayer 一致
        if (animatable.isInvisible()) return;

        // 主模型这一遍没画（buffer 为 null）时 renderForBone 不会跑，也就没有姿势可用
        if (buffer == null || eyes.isEmpty()) return;

        RenderType pupilRenderType = RenderType.entityTranslucentEmissive(getRenderer().getTextureLocation(animatable));

        // 眼窝先画、瞳孔后画：自发光那一遍不写深度，顺序上让瞳孔压在纯白上面
        List<Map.Entry<String, Pose>> ordered = new ArrayList<>(eyes.entrySet());
        ordered.sort(Comparator.comparingInt(entry -> SOCKET_BONES.contains(entry.getKey()) ? 0 : 1));

        for (Map.Entry<String, Pose> entry : ordered) {
            Pose eye = entry.getValue();
            boolean socket = SOCKET_BONES.contains(entry.getKey());

            // 每根骨骼都重新取一次 buffer：换渲染类型会结束上一批，旧 buffer 不能再用
            var eyeBuffer = bufferSource.getBuffer(socket ? SOCKET_RENDER_TYPE : pupilRenderType);

            poseStack.pushPose();
            poseStack.last().pose().set(eye.pose());
            poseStack.last().normal().set(eye.normal());

            writeCubesWithFlatLight(poseStack, eye.bone(), eyeBuffer, packedLight, packedOverlay,
                    socket ? SOCKET_COLOUR : PUPIL_COLOUR);

            poseStack.popPose();
        }
    }

    /**
     * 把一根骨骼的方块写进缓冲区，顶点法线统一用 (0,1,0)，见类注释。
     *
     * <p>几何变换与 {@code GeoRenderer#renderCube} 一致：先按方块的 pivot / 旋转摆好姿态，
     * 再把每个顶点从方块坐标系变换到当前 pose 里。
     */
    private void writeCubesWithFlatLight(PoseStack poseStack, GeoBone bone, VertexConsumer buffer,
                                         int packedLight, int packedOverlay, int colour) {
        for (GeoCube cube : bone.getCubes()) {
            poseStack.pushPose();
            RenderUtil.translateToPivotPoint(poseStack, cube);
            RenderUtil.rotateMatrixAroundCube(poseStack, cube);
            RenderUtil.translateAwayFromPivotPoint(poseStack, cube);

            Matrix4f pose = new Matrix4f(poseStack.last().pose());

            for (GeoQuad quad : cube.quads()) {
                if (quad == null) continue;

                for (GeoVertex vertex : quad.vertices()) {
                    Vector4f position = pose.transform(new Vector4f(vertex.position(), 1.0F));

                    buffer.addVertex(position.x(), position.y(), position.z(), colour,
                            vertex.texU(), vertex.texV(), packedOverlay, packedLight, 0, 1, 0);
                }
            }

            poseStack.popPose();
        }
    }
}
