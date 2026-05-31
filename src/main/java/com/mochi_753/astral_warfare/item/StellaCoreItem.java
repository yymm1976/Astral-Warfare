package com.mochi_753.astral_warfare.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

// 星穹核心 - BOSS 专属战利品
// 蕴含星穹唤星者残余能量的核心，用于后续合成系统的关键材料
// 当前作为纪念性掉落物，未来可扩展为合成虚空长戟升级版的材料
public class StellaCoreItem extends Item {

    public StellaCoreItem() {
        super(new Properties()
                .rarity(Rarity.EPIC)
                .stacksTo(16)
                .fireResistant()
        );
    }
}
