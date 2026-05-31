#version 150

// 黑洞引力透镜后处理 — 片段着色器（优化版）
// 在屏幕空间模拟引力透镜扭曲效果：
//   1. 计算像素到黑洞中心的距离
//   2. 在半径内应用平滑衰减的纹理坐标偏移（引力透镜）
//   3. 叠加旋转分量（切线方向偏移，模拟吸积盘旋转）
//   4. 靠近中心的区域暗化并叠加深紫色色调（事件视界）
//   5. 新增：吸积盘光环（紫色-品红渐变环带）
//   6. 新增：事件视界核心（纯黑圆心）
//   7. 新增：引力透镜色差（RGB通道分离）
//
// 内置 uniform（由 PostPass 自动设置）：
//   DiffuseSampler — 输入帧缓冲纹理
//   ProjMat / InSize / OutSize / Time / ScreenSize — 标准后处理 uniform
// 自定义 uniform（由 SingularityRenderer 手动设置）：
//   CenterPos — 黑洞在屏幕空间的坐标（像素）
//   Radius — 扭曲半径（像素）
//   Intensity — 扭曲强度（0~1）
//   AnimTime — 持续递增的动画时间（用于旋转动画，不受 PostPass Time 循环重置影响）

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 OutSize;
uniform vec2 CenterPos;
uniform float Radius;
uniform float Intensity;
uniform float AnimTime;

in vec2 texCoord;
out vec4 fragColor;

// 平滑步进函数：用于创建柔和的过渡边缘
float smoothstep(float edge0, float edge1, float x) {
    float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

void main() {
    vec2 screenPos = texCoord * OutSize;
    vec2 center = CenterPos;
    vec2 toCenter = center - screenPos;
    float dist = length(toCenter);

    vec2 offsetCoord = texCoord;
    vec4 finalColor = texture(DiffuseSampler, texCoord);

    if (dist < Radius) {
        // 归一化距离：0 = 中心，1 = 边缘
        float normalizedDist = dist / Radius;

        // 事件视界核心：半径 8% 内纯黑
        float eventHorizonRadius = 0.08;
        float eventHorizonMask = 1.0 - smoothstep(0.0, eventHorizonRadius, normalizedDist);

        // 吸积盘光环：在半径 15%-35% 之间的紫色-品红渐变环带
        float accretionInner = 0.15;
        float accretionOuter = 0.35;
        float accretionMask = smoothstep(accretionInner, accretionInner + 0.05, normalizedDist)
                            * (1.0 - smoothstep(accretionOuter - 0.05, accretionOuter, normalizedDist));

        // 引力透镜衰减：使用平滑曲线替代二次衰减
        // 越靠近中心扭曲越强，但保留平滑过渡
        float lensFalloff = pow(1.0 - normalizedDist, 2.5);
        lensFalloff *= (1.0 - eventHorizonMask); // 事件视界内无扭曲

        // 引力透镜方向：指向黑洞中心
        vec2 dir = normalize(toCenter);

        // 旋转分量：基于当前方向角度 + 动画时间偏移
        float angle = atan(dir.y, dir.x) + AnimTime * 0.5;
        vec2 rotDir = vec2(cos(angle), sin(angle));

        // 主扭曲：纹理坐标向黑洞中心偏移
        offsetCoord += dir * lensFalloff * Intensity * 0.025;
        // 旋转扭曲：切线方向偏移，模拟吸积盘旋转
        offsetCoord += rotDir * lensFalloff * Intensity * 0.008;

        // 色差效果：RGB通道分离（引力透镜导致的光学色差）
        // R通道偏移较小，B通道偏移较大，模拟真实引力透镜的色散
        vec2 redCoord = offsetCoord + dir * lensFalloff * Intensity * 0.003;
        vec2 blueCoord = offsetCoord - dir * lensFalloff * Intensity * 0.003;

        float r = texture(DiffuseSampler, redCoord).r;
        float g = texture(DiffuseSampler, offsetCoord).g;
        float b = texture(DiffuseSampler, blueCoord).b;
        finalColor = vec4(r, g, b, 1.0);

        // 边缘暗化：靠近中心的区域变暗（事件视界）
        float darkness = lensFalloff * lensFalloff * 0.6;
        finalColor.rgb *= (1.0 - darkness);

        // 叠加深紫色色调
        finalColor.rgb = mix(finalColor.rgb, vec3(0.08, 0.0, 0.18), darkness * 0.6);

        // 吸积盘光环：紫色-品红渐变
        // 内环偏紫，外环偏品红，随时间旋转
        float accretionAngle = angle + AnimTime * 2.0;
        vec3 accretionColorInner = vec3(0.6, 0.1, 0.9);  // 深紫
        vec3 accretionColorOuter = vec3(0.9, 0.2, 0.7);  // 品红
        vec3 accretionColor = mix(accretionColorInner, accretionColorOuter,
                                  sin(accretionAngle) * 0.5 + 0.5);
        // 光环亮度脉动
        float accretionPulse = 0.7 + 0.3 * sin(AnimTime * 3.0);
        finalColor.rgb = mix(finalColor.rgb, accretionColor,
                             accretionMask * accretionPulse * 0.4);

        // 事件视界核心：纯黑 + 紫色边缘光晕
        vec3 horizonGlow = vec3(0.4, 0.0, 0.6);
        float glowMask = smoothstep(eventHorizonRadius, eventHorizonRadius * 2.5, normalizedDist)
                       * (1.0 - smoothstep(eventHorizonRadius * 2.5, eventHorizonRadius * 4.0, normalizedDist));
        finalColor.rgb = mix(finalColor.rgb, horizonGlow, glowMask * 0.5);

        // 事件视界核心纯黑
        finalColor.rgb *= (1.0 - eventHorizonMask);
    }

    fragColor = finalColor;
}
