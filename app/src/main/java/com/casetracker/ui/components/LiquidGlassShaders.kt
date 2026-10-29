package com.casetracker.ui.components

/**
 * 液态玻璃 AGSL 着色器（v1.90 · 对齐 KernelSU / Kyant0/AndroidLiquidGlass）
 *
 * 硬约束核对：
 * - 入口 half4 main(float2 coord)，禁止 void main + gl_FragColor；
 * - 坐标原点左上角（与 Canvas 一致），不做 GLSL 左下角翻转；
 * - 无 #define 预处理器，宏全部展开为常量/内联表达式；
 * - 类型 float2/float4/half4，不使用 vec2/mat3；
 * - uniform 由 Kotlin 侧 setFloatUniform / setColorUniform 赋值；
 * - 背景纹理通过 uniform shader content 输入（由
 *   RenderEffect.createRuntimeShaderEffect(shader, "content") 自动绑定 backdrop 层，
 *   不是纯颜色）；
 * - 颜色运算：官方库与 KernelSU 均直接在 sRGB 上采样（AGSL 的 content.eval 返回
 *   sRGB 值）。如需严格线性空间，应包 toLinearSrgb/fromLinearSrgb —— 这两个函数
 *   在 AGSL 中的支持情况需对照 Android 官方文档校验后再启用，本文件默认不启用，
 *   与 KernelSU 行为保持一致。
 */

// 圆角矩形 SDF（官方 RoundedRectSDF，const 展开、无宏）
private const val ROUNDED_RECT_SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}"""

/**
 * 液滴折射透镜（Snell 折射 + 七色色散 + 深度法线）。
 * Kotlin 侧赋值的 uniform：
 *   size               -> 液滴尺寸（setFloatUniform("size", w, h)）
 *   offset             -> 裁剪偏移（setFloatUniform("offset", x, y)）
 *   cornerRadii        -> 四角半径（setFloatUniform("cornerRadii", tl, tr, br, bl)）
 *   refractionHeight   -> 折射边缘带高度，按压增强（10dp×press）
 *   refractionAmount   -> 折射强度（14dp×press）
 *   depthEffect        -> 深度法线强度（0 或 1）
 *   chromaticAberration-> 色散强度（KernelSU 对齐 0.5）
 *   content            -> 背景纹理（createRuntimeShaderEffect 自动绑定，非 setInputShader）
 */
val LiquidLensShader = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    // Snell 折射：边缘带内按法线偏移背景采样坐标
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    // 色散：R/B 通道使用微小差异采样偏移，边缘带内形成微弱彩虹边
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}"""

/**
 * Fresnel 边缘光（可选陀螺仪高光：Kotlin 侧把传感器角度写入 angle uniform）。
 * Kotlin 侧赋值 uniform：
 *   size / cornerRadii / angle / falloff / color（layout(color)）
 */
val LiquidHighlightShader = """
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float angle;
uniform float falloff;

$ROUNDED_RECT_SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    return color * intensity;
}"""
