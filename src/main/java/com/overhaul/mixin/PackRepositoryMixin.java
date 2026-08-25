package com.overhaul.mixin;

import java.util.LinkedHashSet;
import java.util.Set;

import com.overhaul.core.data.RuntimeDataPack;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds Overhaul's in-memory pack to every pack repository, data and resource alike.
 *
 * <p>The pack serves data on the server side and assets on the client side, so both repositories
 * want it. It is registered as required and fixed so that a player cannot switch off the recipes
 * for items the mod has already added to the registries.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
	@Shadow
	@Final
	@Mutable
	private Set<RepositorySource> sources;

	@Inject(method = "<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V", at = @At("RETURN"))
	private void overhaul$addGeneratedPack(RepositorySource[] initialSources, CallbackInfo ci) {
		// The replacement must stay mutable. Vanilla stores an immutable set here, but the Fabric
		// resource loader swaps it for a LinkedHashSet in this same constructor and then adds to it
		// later — the world creation screen does exactly that. Putting an immutable set back would
		// leave that call throwing, and only when a player creates a new world.
		Set<RepositorySource> combined = new LinkedHashSet<>(this.sources);
		combined.add(RuntimeDataPack.instance());
		this.sources = combined;
	}
}
