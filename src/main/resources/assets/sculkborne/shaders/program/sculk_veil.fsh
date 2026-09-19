#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D NoiseSample;
uniform sampler2D DeepDarkMask;
uniform mat4 InverseProjectionMatrix;
uniform mat4 InverseModelViewMatrix;
uniform vec3 CameraPos;
uniform float GameTime;
uniform float fadeProgress;
uniform float fogRadius;
uniform int   fogSteps;
uniform float fogDensityStrength;
uniform vec3  fogColor;
uniform float darknessStrength;
uniform float marchRadius;
uniform vec2  maskOrigin;
uniform float maskScale;
uniform float maskSize;
uniform float useMask;

in vec2 texCoord;
out vec4 fragColor;

vec3 getWorldRayDir(vec2 uv) {
    // ndc at far plane (z = 1.0) -> view pos -> world pos
    vec4 ndc = vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec4 viewPos = InverseProjectionMatrix * ndc;
    // 标准做法：除以 w 得到 view-space position
    viewPos /= viewPos.w;
    // 把 view-space 方向转到 world-space（注意 w = 0 保证只变方向，不受平移影响）
    vec4 worldDir4 = InverseModelViewMatrix * vec4(viewPos.xyz, 0.0);
    return normalize(worldDir4.xyz);
}
vec3 getWorldPos(float depth, vec2 uv) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth, 1.0);
    vec4 viewPos = InverseProjectionMatrix * ndc;
    viewPos /= viewPos.w;
    vec4 worldPos = InverseModelViewMatrix * viewPos;
    return worldPos.xyz + CameraPos;
}

// GLSL 3D simplex noise function
// Author : Ian McEwan, Ashima Arts
vec4 permute(vec4 x){ return mod(((x*34.0)+1.0)*x, 289.0); }
vec4 taylorInvSqrt(vec4 r){ return 1.79284291400159 - 0.85373472095314 * r; }
float snoise(vec3 v){
    const vec2  C = vec2(1.0/6.0, 1.0/3.0);
    const vec4  D = vec4(0.0, 0.5, 1.0, 2.0);

    // First corner
    vec3 i  = floor(v + dot(v, C.yyy));
    vec3 x0 =   v - i + dot(i, C.xxx);

    // Other corners
    vec3 g = step(x0.yzx, x0.xyz);
    vec3 l = 1.0 - g;
    vec3 i1 = min(g.xyz, l.zxy);
    vec3 i2 = max(g.xyz, l.zxy);

    //  x0 = x0 - 0. + 0.0 * C
    vec3 x1 = x0 - i1 + 1.0 * C.xxx;
    vec3 x2 = x0 - i2 + 2.0 * C.xxx;
    vec3 x3 = x0 - 1. + 3.0 * C.xxx;

    // Permutations
    i = mod(i, 289.0);
    vec4 p = permute(permute(permute(
    i.z + vec4(0.0, i1.z, i2.z, 1.0))
    + i.y + vec4(0.0, i1.y, i2.y, 1.0))
    + i.x + vec4(0.0, i1.x, i2.x, 1.0));

    // Gradients
    // ( N*N points uniformly over a square, mapped onto an octahedron.)
    float n_ = 1.0/7.0;// N=7
    vec3  ns = n_ * D.wyz - D.xzx;

    vec4 j = p - 49.0 * floor(p * ns.z *ns.z);//  mod(p,N*N)

    vec4 x_ = floor(j * ns.z);
    vec4 y_ = floor(j - 7.0 * x_);// mod(j,N)

    vec4 x = x_ *ns.x + ns.yyyy;
    vec4 y = y_ *ns.x + ns.yyyy;
    vec4 h = 1.0 - abs(x) - abs(y);

    vec4 b0 = vec4(x.xy, y.xy);
    vec4 b1 = vec4(x.zw, y.zw);

    vec4 s0 = floor(b0)*2.0 + 1.0;
    vec4 s1 = floor(b1)*2.0 + 1.0;
    vec4 sh = -step(h, vec4(0.0));

    vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy;
    vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww;

    vec3 p0 = vec3(a0.xy, h.x);
    vec3 p1 = vec3(a0.zw, h.y);
    vec3 p2 = vec3(a1.xy, h.z);
    vec3 p3 = vec3(a1.zw, h.w);

    //Normalise gradients
    vec4 norm = taylorInvSqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;

    // Mix final noise value
    vec4 m = max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
    m = m * m;
    return 42.0 * dot(m*m, vec4(dot(p0, x0), dot(p1, x1),
    dot(p2, x2), dot(p3, x3)));
}

float fbm3(vec3 p) {
    float f = 0.0;
    float amp = 0.5;
    mat3 rot = mat3(
    0.00, 0.80, 0.60,
    -0.80, 0.36, -0.48,
    -0.60, -0.48, 0.64
    );
    for (int i = 0; i < 2; ++i) {
        f += amp * snoise(p);
        p = rot * p * 2.0 + 10.0;
        amp *= 0.55;
    }
    return f;
}
vec3 flowWarp(vec3 p, float time) {
    vec3 q = vec3(
    fbm3(p + vec3(0.0, 0.0, time * 0.05)),
    fbm3(p + vec3(13.5, 9.2, time * 0.07)),
    fbm3(p + vec3(5.3, 17.8, time * 0.09))
    );
    return p + q * 0.5;// 控制扰动强度
}

void main() {
    vec3 baseColor = texture(DiffuseSampler, texCoord).rgb;
    if (GameTime < 0) { fragColor = vec4(baseColor, 1.0); return; }
    float depth = texture(DepthSampler, texCoord).r;
    vec3 surfacePos = getWorldPos(depth, texCoord);
    float surfaceDist = length(surfacePos - CameraPos);

    // 第 1 层：类似原版 Darkness 的距离黑雾。
    float darknessEnd = max(0.001, fogRadius);
    float darkFactor = clamp(1.0 - (darknessEnd - surfaceDist) / darknessEnd, 0.0, 1.0);
    // darknessStrength：黑雾开关/强度，0 = 关闭。
    darkFactor = min(darkFactor, 1) * fadeProgress * darknessStrength;
    vec3 darkened = mix(baseColor, vec3(0.0), darkFactor);

    // 第 2 层：真正体积雾（Beer-Lambert raymarch）
    vec3 rayDir = getWorldRayDir(texCoord);
    float fogAcc = 0.0;
    vec3 fogCol = fogColor;
    float radius = max(0.001, fogRadius);
    int steps = clamp(fogSteps, 1, 64);   // 作为最小步数
    // 远处深暗之域：采到 marchRadius，步长约 6 格，距离越远自动加步数。
    float marchEnd = min(surfaceDist, max(marchRadius, radius));
    int n = clamp(max(int(ceil(marchEnd / 6.0)), steps), 1, 64);
    float stepSize = marchEnd / float(n);

    float T = 1.0;                 // 透射率：光线还剩多少
    vec3 scattered = vec3(0.0);    // 累积的雾散射颜色

    vec3 rayPos = CameraPos;
    for (int i = 0; i < n; i++) {
        rayPos += rayDir * stepSize;

        // 深暗之域掩码：useMask=1 时限制在深暗之域，否则全区域（buff 模式）。
        float mask = useMask > 0.5
            ? texture(DeepDarkMask, (rayPos.xz - maskOrigin) / (maskScale * maskSize)).r
            : 1.0;
        if (mask < 0.5) continue;

        float noise = fbm3(flowWarp(rayPos * 0.12, GameTime * 0.08));
        // 密度：噪声加正偏置，避免平均密度趋近 0
        float density = smoothstep(0.0, 0.95, noise * 0.5 + 0.5);
        // 距离衰减：近处淡、远处浓，立体感的来源，同时替代原来的边界硬切
//        density *= smoothstep(0., radius * 0.8, length(rayPos - CameraPos));
        if(i < 1) fogCol *= noise;
        if (density > 0.001) {
            float ext = density * stepSize * max(0.01, fogDensityStrength);
            float segT = exp(-ext);
            scattered += T * (1.0 - segT) * fogCol;
            T *= segT;
        }
    }

    vec3 volColor = darkened * T + scattered;
    vec3 finalColor = mix(darkened, volColor, fadeProgress);
    fragColor = vec4(finalColor, 1);
}
