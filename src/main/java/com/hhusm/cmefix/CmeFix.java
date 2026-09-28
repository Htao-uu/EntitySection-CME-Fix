package com.hhusm.cmefix;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * EntitySection CME Fix(1.20.1)—— 纯服务端/双端的原版缺陷修复 mod。
 *
 * <p>只做一件事:把 {@code PersistentEntitySectionManager} 里"边遍历边可能被修改"的
 * {@code Stream.forEach} 换成"先快照再遍历",消除服务端在实体大量增删时抛出的
 * {@code ConcurrentModificationException}(表现为随机的"Exception in server tick loop"崩服)。
 *
 * <p>移植自 <a href="https://github.com/alppp/EntitySectionManager_CME_Fix">EntityGuardian</a>
 * (作者 AlpDerps,MIT 许可);上游针对 1.21 的 {@code updateChunkStatus},这里是 1.20.1 的对应方法。
 */
@Mod(CmeFix.MODID)
public class CmeFix {

    public static final String MODID = "cme_fix";
    private static final Logger LOGGER = LogUtils.getLogger();

    public CmeFix() {
        LOGGER.info("[CME Fix] 已加载:PersistentEntitySectionManager 的并发修改问题修复生效(1.20.1)");
    }
}
