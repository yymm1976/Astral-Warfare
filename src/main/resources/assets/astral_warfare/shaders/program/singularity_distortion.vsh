#version 150

// 黑洞引力透镜后处理 — 顶点着色器
// 标准后处理顶点着色器：将全屏四边形变换到屏幕空间
// Position 属性由 PostPass 自动提供（全屏四边形的顶点位置）
// ProjMat 由 PostPass 自动设置为正交投影矩阵
in vec4 Position;
uniform mat4 ProjMat;
uniform vec2 InSize;
uniform vec2 OutSize;
out vec2 texCoord;

void main() {
    vec4 outPos = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);
    texCoord = Position.xy / OutSize;
}
