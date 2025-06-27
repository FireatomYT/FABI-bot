package dev.fireatom.FABI.listeners;

import ch.qos.logback.classic.Logger;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.utils.database.DBUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class GuildListener extends ListenerAdapter {

	private final Logger log = (Logger) LoggerFactory.getLogger(GuildListener.class);

	private final App bot;
	private final DBUtil db;

	public GuildListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGuildJoin(@NotNull GuildJoinEvent event) {
		Guild guild = event.getGuild();
		if (bot.getCheckUtil().isBlacklisted(guild)) {
			guild.leave().queue();
			log.info("Auto-left new guild '{}'({}) BLACKLIST!", guild.getName(), guild.getId());
		} else {
			log.info("Joined guild '{}'({})", guild.getName(), guild.getId());
		}
	}

	@Override
	public void onGuildLeave(@NotNull GuildLeaveEvent event) {
		long guildId = event.getGuild().getIdLong();
		log.info("Left guild '{}'({})", event.getGuild().getName(), guildId);

		// Deletes every information connected to this guild from bot's DB (except ban tables)
		// May be dangerous, but provides privacy
		for (Integer groupId : db.group.getGuildGroups(guildId)) {
			try {
				bot.getLogger().group.onGuildLeft(event.getGuild(), groupId);
			} catch (Exception ignored) {}
		}
		String ownerIcon = event.getGuild().getIconUrl();
		for (Integer groupId : db.group.getOwnedGroups(guildId)) {
			try {
				bot.getLogger().group.onDeletion(guildId, ownerIcon, groupId);
				db.group.clearGroup(groupId);
			} catch (Exception ignored) {}
		}
		ignoreExc(() -> db.group.removeGuildFromGroups(guildId));
		ignoreExc(() -> db.group.deleteGuildGroups(guildId));

		ignoreExc(() -> db.access.removeAll(guildId));
		ignoreExc(() -> db.webhook.removeAll(guildId));
		ignoreExc(() -> db.verifySettings.remove(guildId));
		ignoreExc(() -> db.ticketSettings.remove(guildId));
		ignoreExc(() -> db.roles.removeAll(guildId));
		ignoreExc(() -> db.guildVoice.remove(guildId));
		ignoreExc(() -> db.ticketPanels.deleteAll(guildId));
		ignoreExc(() -> db.ticketTags.deleteAll(guildId));
		ignoreExc(() -> db.tempRoles.removeAll(guildId));
		ignoreExc(() -> db.autopunish.removeGuild(guildId));
		ignoreExc(() -> db.strikes.removeGuild(guildId));
		ignoreExc(() -> db.logs.removeGuild(guildId));
		ignoreExc(() -> db.logExemptions.removeGuild(guildId));
		ignoreExc(() -> db.modifyRole.removeAll(guildId));
		ignoreExc(() -> db.games.removeGuild(guildId));
		ignoreExc(() -> db.persistent.removeGuild(guildId));
		ignoreExc(() -> db.modReport.removeGuild(guildId));
		
		ignoreExc(() -> db.guildSettings.remove(guildId));

		log.info("Automatically removed guild '{}'({}) from db.", event.getGuild().getName(), guildId);
	}

	private void ignoreExc(RunnableExc runnable) {
		try {
			runnable.run();
		} catch (SQLException ignored) {}
	}

	@FunctionalInterface public interface RunnableExc { void run() throws SQLException; }
}
