package dev.fireatom.FABI.utils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.objects.CaseType;
import dev.fireatom.FABI.utils.database.DBUtil;
import dev.fireatom.FABI.utils.logs.GuildLogger;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

public class GroupHelper {

	private final JDA JDA;
	private final GuildLogger logger;
	private final DBUtil db;

	public GroupHelper(App bot) {
		this.JDA = bot.JDA;
		this.logger = bot.getLogger();
		this.db = bot.getDBUtil();
	}

	private void banUser(int groupId, Guild executedGuild, User target, String reason, String modName) {
		final List<Long> guildIds = new ArrayList<>();
		for (long guildId : db.group.getGroupManagers(groupId)) {
			for (int subGroupId : db.group.getOwnedGroups(guildId)) {
				guildIds.addAll(db.group.getGroupMembers(subGroupId));
			}
		}
		guildIds.addAll(db.group.getGroupMembers(groupId));
		if (guildIds.isEmpty()) return;

		final int maxCount = guildIds.size();
		final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		final String newReason = "Sync #%s by @%s: %s".formatted(groupId, modName, reason);
		for (long guildId : guildIds) {
			final Guild guild = JDA.getGuildById(guildId);
			if (guild == null) continue;
			// fail-safe check if the target has temporal ban (to prevent auto unban)
			try {
				db.cases.setInactiveByType(target.getIdLong(), guildId, CaseType.BAN);
			} catch (SQLException ignored) {}

			completableFutures.add(guild.ban(target, 0, TimeUnit.SECONDS).reason(newReason).submit());
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((done, exception) -> {
				int banned = 0;
				for (CompletableFuture<Void> future : completableFutures) {
					if (!future.isCompletedExceptionally()) banned++;
				}
				// Log in server where 
				logger.mod.onHelperSyncBan(groupId, executedGuild, target, reason, banned, maxCount);
			});
	}

	private void unbanUser(int groupId, Guild master, User target, String reason, String modName) {
		final List<Long> guildIds = new ArrayList<>();
		for (long guildId : db.group.getGroupManagers(groupId)) {
			for (int subGroupId : db.group.getOwnedGroups(guildId)) {
				guildIds.addAll(db.group.getGroupMembers(subGroupId));
			}
		}
		guildIds.addAll(db.group.getGroupMembers(groupId));
		if (guildIds.isEmpty()) return;

		final int maxCount = guildIds.size();
		final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		final String newReason = "Sync #%s by @%s: %s".formatted(groupId, modName, reason);
		for (long guildId : guildIds) {
			final Guild guild = JDA.getGuildById(guildId);
			if (guild == null) continue;
			// Remove temporal ban case
			try {
				db.cases.setInactiveByType(target.getIdLong(), guildId, CaseType.BAN);
			} catch (SQLException ignored) {}

			completableFutures.add(guild.unban(target).reason(newReason).submit());
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((done, exception) -> {
				int unbanned = 0;
				for (CompletableFuture<Void> future : completableFutures) {
					if (!future.isCompletedExceptionally()) unbanned++;
				}
				logger.mod.onHelperSyncUnban(groupId, master, target, reason, unbanned, maxCount);
			});
	}

	private void kickUser(int groupId, Guild master, User target, String reason, String modName) {
		final List<Long> guildIds = new ArrayList<>();
		for (long guildId : db.group.getGroupManagers(groupId)) {
			for (int subGroupId : db.group.getOwnedGroups(guildId)) {
				guildIds.addAll(db.group.getGroupMembers(subGroupId));
			}
		}
		guildIds.addAll(db.group.getGroupMembers(groupId));
		if (guildIds.isEmpty()) return;

		final int maxCount = guildIds.size();
		final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
		final String newReason = "Sync #%s by @%s: %s".formatted(groupId, modName, reason);
		for (long guildId : guildIds) {
			final Guild guild = JDA.getGuildById(guildId);
			if (guild == null) continue;

			completableFutures.add(guild.kick(target).reason(newReason).submit());
		}

		CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
			.whenComplete((done, exception) -> {
				int kicked = 0;
				for (CompletableFuture<Void> future : completableFutures) {
					if (!future.isCompletedExceptionally()) kicked++;
				}
				logger.mod.onHelperSyncKick(groupId, master, target, reason, kicked, maxCount);
			});
	}

	public void runBan(int groupId, Guild executedGuild, User user, String reason, String modName) {
		CompletableFuture.runAsync(() -> {
			banUser(groupId, executedGuild, user, Optional.ofNullable(reason).orElse("none"), modName);
		});
	}

	public void runUnban(int groupId, Guild master, User user, String reason, String modName) {
		CompletableFuture.runAsync(() -> {
			unbanUser(groupId, master, user, Optional.ofNullable(reason).orElse("none"), modName);
		});
	}

	public void runKick(int groupId, Guild master, User user, String reason, String modName) {
		CompletableFuture.runAsync(() -> {
			kickUser(groupId, master, user, Optional.ofNullable(reason).orElse("none"), modName);
		});
	}
}
