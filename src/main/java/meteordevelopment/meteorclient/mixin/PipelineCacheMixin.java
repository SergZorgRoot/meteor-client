/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

/**
 * Works around a deadlock in 26.3's lazy pipeline compilation.
 * <p>
 * {@link PipelineCache#get} compiles pipelines that were not precompiled on the shared background executor and then
 * blocks the render thread with {@code join()}. Chunk section compile tasks on that same executor spin-wait inside
 * {@code SectionRenderDispatcher$RenderSection$CompileTask#doTask} until the render thread flushes the terrain
 * uber buffer, so if every worker is spinning (which happens right after joining a world or changing video settings)
 * the compile task never runs and the render thread waits forever. Any mod that requests a pipeline mid-frame
 * (Xaero's minimap waypoint renderer, for example) triggers it.
 * <p>
 * Compiling inline on the render thread removes the dependency on the worker pool. The cost is a one-off stall of a
 * few milliseconds the first time each such pipeline is used.
 */
@Mixin(PipelineCache.class)
public abstract class PipelineCacheMixin {
    @ModifyArg(method = "get", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuDevice;compilePipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 2)
    private Executor meteor$compileInlineOnRenderThread(Executor executor) {
        return RenderSystem.isOnRenderThread() ? Runnable::run : executor;
    }
}
