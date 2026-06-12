package com.mochi_753.astral_warfare.client.postprocess;

import com.mochi_753.astral_warfare.AstralWarfare;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import team.lodestar.lodestone.systems.postprocess.PostProcessor;

// 黑洞引力透镜后处理器 — 基于 Lodestone PostProcessor 系统
// 替代原有的 SingularityRenderer 手动 PostChain 管理方式
// Lodestone PostProcessor 自动处理 PostChain 生命周期（初始化、缩放、关闭）
// 以及深度缓冲复制和通用 uniform 设置
public class SingularityPostProcessor extends PostProcessor {

    // 单例实例，由 PostProcessHandler.addInstance() 注册后全局使用
    public static final SingularityPostProcessor INSTANCE = new SingularityPostProcessor();

    // 当前帧的黑洞屏幕坐标和着色器参数
    // 由 SingularityRenderHandler 在 AFTER_TRANSLUCENT_BLOCKS 阶段更新
    // C-06修复：声明为 volatile，保证跨线程可见性（写于渲染线程，读于后处理线程）
    private volatile float screenX;
    private volatile float screenY;
    private volatile float radius;
    private volatile float intensity;
    private volatile float animTime;
    // 是否检测到黑洞实体（由外部事件监听器设置）
    private volatile boolean singularityDetected;

    // 返回后处理链的资源路径
    // 对应 shaders/post/singularity_distortion.json
    @Override
    public ResourceLocation getPostChainLocation() {
        return ResourceLocation.fromNamespaceAndPath(AstralWarfare.MOD_ID, "singularity_distortion");
    }

    // 后处理执行前回调：设置自定义 uniform
    // Lodestone 在 applyPostProcess() 中自动调用此方法
    // viewModelMatrix 由 PostProcessHandler 在 AFTER_PARTICLES 阶段捕获
    @Override
    public void beforeProcess(Matrix4f viewModelMatrix) {
        if (!singularityDetected) return;
        // 遍历所有 EffectInstance 设置自定义 uniform
        // effects 数组由 PostProcessor.loadPostChain() 从 JSON 解析生成
        for (int i = 0; i < effects.length; i++) {
            effects[i].safeGetUniform("CenterPos").set(screenX, screenY);
            effects[i].safeGetUniform("Radius").set(radius);
            effects[i].safeGetUniform("Intensity").set(intensity);
            effects[i].safeGetUniform("AnimTime").set(animTime);
        }
    }

    // 后处理执行后回调：无需额外操作
    // Lodestone 已自动重新绑定主渲染目标
    @Override
    public void afterProcess() {
    }

    // 由 SingularityRenderHandler 调用：更新黑洞检测数据并激活处理器
    // 必须在 AFTER_LEVEL 之前调用（即 AFTER_TRANSLUCENT_BLOCKS 阶段）
    // 因为 PostProcessHandler 在 AFTER_LEVEL 阶段调用 applyPostProcess()
    // applyPostProcess() 开头检查 isActive，若为 false 则直接返回
    public void updateSingularityData(float screenX, float screenY, float radius, float intensity, float animTime) {
        this.screenX = screenX;
        this.screenY = screenY;
        this.radius = radius;
        this.intensity = intensity;
        this.animTime = animTime;
        this.singularityDetected = true;
        setActive(true);
    }

    // 由 SingularityRenderHandler 调用：清除黑洞检测状态并停用处理器
    // setActive(false) 同时会将 time 重置为 0
    public void clearSingularity() {
        this.singularityDetected = false;
        setActive(false);
    }
}
