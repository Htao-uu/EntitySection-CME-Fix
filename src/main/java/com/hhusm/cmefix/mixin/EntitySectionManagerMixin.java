package com.hhusm.cmefix.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 修原版 {@code PersistentEntitySectionManager} 的 ConcurrentModificationException。
 *
 * <p><b>崩溃现场</b>(实测,四次同栈):
 * <pre>
 * java.util.ConcurrentModificationException
 *   at java.util.ArrayList$Itr.checkForComodification
 *   at ...Iterator.forEachRemaining
 *   at java.util.stream.ReferencePipeline.forEach
 *   at PersistentEntitySectionManager.m_157527_(...)
 *   at PersistentEntitySectionManager.m_287207_(...)      ← tick
 *   at ChunkMap.m_287285_ ← ChunkHolder ← DistanceManager ...
 * </pre>
 *
 * <p><b>原因</b>(对着 1.20.1 源码核实):{@code m_157527_} 对"这个区块的实体流"做
 * {@code stream.forEach(...)},而回调里会增删实体({@code m_157570_}/{@code m_157580_}/
 * {@code m_157575_}/{@code m_157564_} 就是"实体加入/移除 TICKING 列表"的处理)。
 * 只要期间底层 {@code ArrayList} 被改,外层迭代器就抛 CME ——
 * <b>谁被遍历谁报错,所以崩溃栈里永远看不到真凶</b>。
 *
 * <p><b>修法</b>:这几处 {@code Stream.forEach} 一律重定向为"先 {@code toList()} 快照,再遍历快照"。
 *
 * <p><b>为什么用 SRG 名 + {@code remap = false}</b>:本 mod 没有配置 Mixin 注解处理器(没有 refmap),
 * 而正式服务器上原版方法名就是 SRG 名({@code m_157527_} 等,直接从崩溃栈读出的真实运行名),
 * 因此 {@code remap = false} + SRG 名**无需 refmap 即可准确命中**;代价只是开发环境不生效(仅警告)。
 *
 * <p><b>安全设计</b>:目标类写字符串(不提前加载任何类);{@code defaultRequire: 0},万一与其它 mod
 * 在同一调用点冲突,**失败的是本 mod 并只记警告**,绝不会连累别人的必装 mixin;快照异常时退回原语义。
 */
@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager", remap = false)
public abstract class EntitySectionManagerMixin {

    @Redirect(
            method = {
                    // ★ 实测崩溃栈直接给出的那个 lambda(属于 m_157548_(Writer) = save 的调试导出):
                    //   PersistentEntitySectionManager.m_157543_(:169) -> ReferencePipeline.forEach
                    //   -> Iterator.forEachRemaining -> ArrayList$Itr.checkForComodification
                    "m_157543_",
                    "m_157527_",   // updateChunkStatus(ChunkPos, Visibility):早期四次同栈崩溃的那一层
                    "m_157552_",   // 处理"待加入"实体的流
                    "m_157559_"    // 处理"待移除"实体的流
            },
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V"
            ),
            remap = false)
    // ★★★ 必须是**实例方法**(不能 static):Mixin 要求 @Redirect 处理器的 static 修饰符与目标方法一致,
    //     而这些目标方法都是实例方法 —— 写成 static 会让整个 mixin 应用失败(实测踩过,四次崩溃全是这个原因)。
    private <T> void cme_fix$snapshotForEach(Stream<T> stream, Consumer<? super T> consumer) {
        try {
            List<T> snapshot = stream.toList();     // 先快照:之后集合怎么变都与本次迭代无关
            snapshot.forEach(consumer);
        } catch (Throwable t) {
            stream.forEach(consumer);               // 兜底:退回原语义
        }
    }
}
