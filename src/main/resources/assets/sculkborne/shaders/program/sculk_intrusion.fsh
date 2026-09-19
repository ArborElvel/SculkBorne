#version 330 compatibility

// SculkIntrusion 全屏侵入纹理（由 Shadertoy 版 sculk_intrusion.glsl 转换）：
//   fragCoord   -> gl_FragCoord.xy（像素坐标）
//   iResolution -> OutSize（PostPass 每帧注入的目标尺寸）
//   iTime       -> GameTime（秒，由 SculkIntrusionRenderer 注入）
//   fadeProgress -> 淡入淡出：0 = 纯场景，1 = 达到设定的最大覆盖强度
uniform sampler2D DiffuseSampler;
uniform float GameTime;
uniform vec2 OutSize;
uniform float fadeProgress;
uniform float randomHash;

in vec2 texCoord;
out vec4 fragColor;

vec3 random_perlin(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 69.5)), dot(p, vec3(269.5, 183.3, 132.7)), dot(p, vec3(247.3, 108.5, 96.5)));
    return -1.0 + 2.0 * fract(cos(p) * 43758.5453123);
}

float noise_perlin(vec3 p) {
    vec3 i = floor(p);
    vec3 s = fract(p);

    float a = dot(random_perlin(i), s);
    float b = dot(random_perlin(i + vec3(1.0, 0.0, 0.0)), s - vec3(1.0, 0.0, 0.0));
    float c = dot(random_perlin(i + vec3(0.0, 1.0, 0.0)), s - vec3(0.0, 1.0, 0.0));
    float d = dot(random_perlin(i + vec3(0.0, 0.0, 1.0)), s - vec3(0.0, 0.0, 1.0));
    float e = dot(random_perlin(i + vec3(1.0, 1.0, 0.0)), s - vec3(1.0, 1.0, 0.0));
    float f = dot(random_perlin(i + vec3(1.0, 0.0, 1.0)), s - vec3(1.0, 0.0, 1.0));
    float g = dot(random_perlin(i + vec3(0.0, 1.0, 1.0)), s - vec3(0.0, 1.0, 1.0));
    float h = dot(random_perlin(i + vec3(1.0, 1.0, 1.0)), s - vec3(1.0, 1.0, 1.0));

    vec3 u = smoothstep(0.0, 1.0, s);

    return mix(mix(mix(a, b, u.x), mix(c, e, u.x), u.y), mix(mix(d, f, u.x), mix(g, h, u.x), u.y), u.z);
}

float noise_turbulence(vec3 p) {
    float f = 0.0;
    float a = 1.0;
    p *= 2.0;

    for (int i = 0; i < 3; i++) {
        float n = abs(noise_perlin(p));
        n = min(n, 0.2);
        f += a * n;
        p *= 2.0;
        a *= 0.5;
    }
    return f;
}

// randomHash 是每次效果触发时由外部（Java）传入的随机种子。
// hash21 不再自产随机：它只负责把这一个种子确定性地摊到不同的节点格上，
// 保证同一帧内各格结果稳定、格子之间互不相关。
float hash21(vec2 p, float seed) {
    p = fract(p * vec2(123.34, 456.21) + seed);
    p += dot(p, p + 45.32 + seed);

    return fract(p.x * p.y);
}

float nodePulse(vec2 nodeCell, float seed) {
    float phase = hash21(nodeCell, seed) * 6.2831853;
    float speed = 0.8 + hash21(nodeCell + 12.0, seed + 0.5) * 0.2;
    float pulse = sin(GameTime * speed + phase);
    pulse = pulse * 0.5 + 0.5;
    return smoothstep(0.05, 0.85, pulse);
}

void main() {
    vec3 baseColor = texture(DiffuseSampler, texCoord).rgb;
    vec2 fragCoord = gl_FragCoord.xy;
    float pixelSize = 12.0;
    vec2 grid = floor(fragCoord / pixelSize);
    vec2 pixelUV = (grid + 0.5) * pixelSize / OutSize;
    vec2 p = (pixelUV - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);

    // 外轮廓
    float shapeNoise = noise_perlin(vec3(p * 2.5 + randomHash * 31.0, GameTime * 0.025));
    shapeNoise = shapeNoise * 0.5 + 0.5;
    float ellipseDist = length(p / vec2(1.11, 0.65));
    float deformation = (shapeNoise - 0.5) * 0.15;
    ellipseDist += deformation;
    float edge = smoothstep(0.45, 1.0, ellipseDist);
    edge = pow(edge, 1.4);

    // Sculk 主体纹理
    float largeNoise = noise_turbulence(vec3(pixelUV * 1.35 + randomHash * 37.0, GameTime * 0.04));
    float smallNoise = noise_turbulence(vec3(pixelUV * 4.5 + randomHash * 43.0, GameTime * 0.01));
    float pattern = largeNoise * 0.83 + smallNoise * 0.92;

    // 压缩动态范围
    pattern = 1.0 - exp(-pattern * 1.15);
    pattern = smoothstep(0.25, 0.62, pattern);
    float intensity = pattern * edge;

    // Sculk 基础颜色
    vec3 darkColor = vec3(0.004, 0.012, 0.018);
    vec3 sculkColor = vec3(0.008, 0.13, 0.17);
    vec3 sculkBright = vec3(0.015, 0.28, 0.34);
    vec3 color = mix(darkColor, sculkColor, intensity);
    color = mix(color, sculkBright, pow(intensity, 2.5) * 0.35);

    // 发光节点
    float glow = 0.0;
    // 一个节点区域占 6×6 像素。不再做 Perlin 局部极大值检测。
    float nodeScale = 6.0;
    vec2 nodeCell = floor(grid / nodeScale);

    // 当前像素在 cell 中的位置
    vec2 local = mod(grid, nodeScale);

    // Hash 决定这个 cell 是否存在节点
    float nodeRandom = hash21(nodeCell, randomHash);
    float nodeExists = step(0.90, nodeRandom);

    // 节点中心固定在 cell 中间。
    vec2 nodeCenter = floor(vec2(nodeScale * 0.5));

    // 当前像素相对于节点中心
    vec2 relative = local - nodeCenter;

    // 精确 5 像素十字
    float d = abs(relative.x) + abs(relative.y);
    float cross = 1.0 - step(1.5, d);
    float pixelWeight = mix(1.0, 0.45, step(0.5, d));
    cross *= pixelWeight;

    // 整个节点使用同一个 cell 的 pulse
    float pulse = nodePulse(nodeCell, randomHash);
    glow = nodeExists * cross * pulse;

    // 节点只在 Sculk 区域出现
    float nodeMask = smoothstep(0.02, 0.08, intensity);
    glow *= nodeMask;

    // 节点颜色
    vec3 glowColor = vec3(0.03, 0.75, 0.85);

    color = mix(color, glowColor, clamp(glow, 0.0, 1.0));
    // 纹理的黑色底几乎铺满整屏，直接整体混合会把整个画面压暗。
    // 因此只让“有幽匿图案”的区域参与叠加（coverage 由 pattern 强度决定），
    // 图案外的场景保持原亮度；coverage 阈值和 0.8 上限都可按观感调整。
    float coverage = smoothstep(0.02, 0.18, intensity);
    // 方向：淡入由外向内（边缘先出现并向中心收缩），淡出由内向外
    // （中心先消失，覆盖区向边缘收缩）。innerBoundary 是覆盖区的内侧边界，
    // 随 fadeProgress 从 1.15 移到 -0.15；边界外侧（更靠屏幕边缘）的像素参与叠加。
    float maxRadius = 0.5 * length(vec2(OutSize.x / OutSize.y, 1.0));
    float innerBoundary = (1.0 - fadeProgress) * 1.3 - 0.15;
    // 扩散边界不规则化：给归一化半径叠加两层 Perlin 扰动（含 randomHash 种子与
    // GameTime 时间轴），边缘像幽匿生长一样参差蠕动，不再是规则的圆。
    float ragged = noise_perlin(vec3(p * 3.0 + randomHash * 37.0, GameTime * 0.04)) * 0.12
                 + noise_perlin(vec3(p * 7.0 - randomHash * 53.0, GameTime * 0.06)) * 0.06;
    float reveal = smoothstep(innerBoundary, innerBoundary + 0.10, length(p) / maxRadius + ragged);
    fragColor = vec4(mix(baseColor, color, fadeProgress * 0.8 * coverage * reveal), 1.0);
}
