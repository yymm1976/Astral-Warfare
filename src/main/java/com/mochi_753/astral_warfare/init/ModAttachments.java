package com.mochi_753.astral_warfare.init;

import com.mochi_753.astral_warfare.AstralWarfare;
import com.mochi_753.astral_warfare.attachment.ManaData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

// Data Attachment 注册中心
// NeoForge 1.21.x 使用 AttachmentType 替代旧版 Capability，通过 DeferredRegister 注册
// AttachmentType 定义了数据的默认值、序列化方式和同步策略
public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, AstralWarfare.MOD_ID);

    // 法力值 Attachment：绑定到实体上的法力数据
    // .serialize(Codec) 启用磁盘持久化（存档保存时自动序列化/反序列化）
    // 不使用 .sync() 自动同步，由自定义网络包 ClientboundStellaManaPacket 手动同步
    // S-14修复：NeoForge 1.21.1 AttachmentType.Builder 无 copyOnResolve() 方法
    // 默认行为已为每个实体创建独立实例，无需额外配置
    public static final Supplier<AttachmentType<ManaData>> MANA =
            ATTACHMENT_TYPES.register("mana", () ->
                    AttachmentType.builder(ManaData::new)
                            .serialize(ManaData.CODEC)
                            .build()
            );
}
