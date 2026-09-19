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

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);

    return fract(p.x * p.y);
}
float nodePulse(vec2 nodeCell) {
    float phase = hash21(nodeCell) * 6.2831853;
    float speed = 0.8 + hash21(nodeCell + 12.0) * 0.2;
    float p = sin(iTime * speed + phase);
    p = p * 0.5 + 0.5;
    return smoothstep(0.05, 0.85, p);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    float pixelSize = 12.0;
    vec2 grid = floor(fragCoord / pixelSize);
    vec2 pixelUV = (grid + 0.5) * pixelSize / iResolution.xy;
    vec2 p = (pixelUV - 0.5) * vec2(iResolution.x / iResolution.y, 1.0);

    // 外轮廓
    float shapeNoise = noise_perlin(vec3(p * 2.5, iTime * 0.025));
    shapeNoise = shapeNoise * 0.5 + 0.5;
    float ellipseDist = length(p / vec2(1.11, 0.70));
    float deformation = (shapeNoise - 0.5) * 0.15;
    ellipseDist += deformation;
    float edge = smoothstep(0.45, 1.0, ellipseDist);
    edge = pow(edge, 1.4);

    // Sculk 主体纹理
    float largeNoise = noise_turbulence(vec3(pixelUV * 1.35, iTime * 0.04));
    float smallNoise = noise_turbulence(vec3(pixelUV * 4.5, iTime * 0.01));
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
    vec2 nodeCell = floor(grid /
    nodeScale);

    //当前像素在 cell 中的位置
    vec2 local = mod(grid, nodeScale);

    // Hash 决定这个 cell 是否存在节点
    float nodeRandom = hash21(nodeCell);
    float nodeExists = step(0.92, nodeRandom);

    // 节点中心固定在 cell 中间。
    vec2 nodeCenter = floor(vec2(nodeScale * 0.5));

    // 当前像素相对于节点中心
    vec2 relative = local - nodeCenter;

    // 精确 5 像素十字
    float d = abs(relative.x) + abs(relative.y);
    float cross = 1.0 - step(1.5, d);
    float pixelWeight = mix(1.0, 0.45, step(0.5, d));
    cross *= pixelWeight;

    //整个节点使用同一个 cell 的 pulse
    float pulse = nodePulse(nodeCell);
    glow = nodeExists * cross * pulse;

    // 节点只在 Sculk 区域出现
    float nodeMask = smoothstep(0.02, 0.08, intensity);
    glow *= nodeMask;

    // 节点颜色
    vec3 glowColor = vec3(0.03, 0.75, 0.85);

    color = mix(color, glowColor, clamp(glow, 0.0, 1.0));
    fragColor = vec4(color, 1.0);
}