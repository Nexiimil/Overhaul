package com.overhaul.mixin;

import com.overhaul.module.multiplayer.MultiplayerModule;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.PlayerTeam;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Releases a team's land when the team itself is deleted.
 *
 * <p>Without this, {@code /team remove} leaves land owned by a name that no longer refers to
 * anything: nobody can build on it, and nobody can join the team that would let them release it.
 * Since claims are built on vanilla teams, the vanilla command that destroys one is the right place
 * to hear about it.
 */
@Mixin(ServerScoreboard.class)
public abstract class ServerScoreboardMixin {
	@Shadow
	@Final
	private MinecraftServer server;

	// onTeamRemoved rather than removePlayerTeam: the latter is declared on the plain Scoreboard,
	// which has no server to hand, and this is the notification the server side overrides to hear
	// about it.
	@Inject(method = "onTeamRemoved", at = @At("HEAD"))
	private void overhaul$releaseTheirLand(PlayerTeam team, CallbackInfo ci) {
		MultiplayerModule.onTeamRemoved(server, team.getName());
	}
}
