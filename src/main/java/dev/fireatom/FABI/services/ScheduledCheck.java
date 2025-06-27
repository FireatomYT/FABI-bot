package dev.fireatom.FABI.services;

import static dev.fireatom.FABI.objects.CaseType.BAN;
import static dev.fireatom.FABI.utils.CastUtil.castLong;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.objects.ReportData;
import dev.fireatom.FABI.utils.database.DBUtil;
import dev.fireatom.FABI.utils.database.managers.CaseManager.CaseData;

import dev.fireatom.FABI.utils.database.managers.LevelManager;
import dev.fireatom.FABI.utils.encoding.EncodingUtil;
import dev.fireatom.FABI.utils.imagegen.renders.ModReportRender;
import dev.fireatom.FABI.utils.level.PlayerObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.TimeUtil;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;


public class ScheduledCheck {

	private final Logger log = (Logger) LoggerFactory.getLogger(ScheduledCheck.class);

	private final App bot;
	private final DBUtil db;

	private final Integer CLOSE_AFTER_DELAY = 12; // hours

	public ScheduledCheck(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	// each 10-15 minutes
	public void irregularChecks() {
		CompletableFuture.runAsync(this::checkTicketStatus)
			.thenRunAsync(this::checkExpiredTempRoles)
			.thenRunAsync(this::checkExpiredStrikes)
			.thenRunAsync(this::checkExpiredPersistentRoles)
			.thenRunAsync(this::generateReport);
	}

	private void checkTicketStatus() {
		try {
			db.tickets.getOpenedChannels().forEach(channelId -> {
				GuildMessageChannel channel = bot.JDA.getChannelById(GuildMessageChannel.class, channelId);
				if (channel == null) {
					// Should be closed???
					ignoreExc(() -> bot.getDBUtil().tickets.forceCloseTicket(channelId));
					return;
				}
				int autocloseTime = db.getTicketSettings(channel.getGuild()).getAutocloseTime();
				if (autocloseTime == 0) return;

				if (TimeUtil.getTimeCreated(channel.getLatestMessageIdLong()).isBefore(OffsetDateTime.now().minusHours(autocloseTime))) {
					Guild guild = channel.getGuild();
					UserSnowflake user = User.fromId(db.tickets.getUserId(channelId));
					Instant closeTime = Instant.now().plus(CLOSE_AFTER_DELAY, ChronoUnit.HOURS);

					MessageEmbed embed = new EmbedBuilder()
						.setColor(db.getGuildSettings(guild).getColor())
						.setDescription(bot.getLocaleUtil().getLocalized(guild.getLocale(), "bot.ticketing.listener.close_auto")
							.replace("{user}", user.getAsMention())
							.replace("{time}", TimeFormat.RELATIVE.atInstant(closeTime).toString())
						)
						.build();

					Button close = Button.primary("ticket:close", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.close"));
					Button cancel = Button.secondary("ticket:cancel", bot.getLocaleUtil().getLocalized(guild.getLocale(), "ticket.cancel"));

					ignoreExc(() -> db.tickets.setRequestStatus(channelId, closeTime.getEpochSecond()));
					channel.sendMessage("||%s||".formatted(user.getAsMention())).addEmbeds(embed).addActionRow(close, cancel).queue();
				}
			});

			db.tickets.getCloseMarkedTickets().forEach(channelId -> {
				GuildChannel channel = bot.JDA.getGuildChannelById(channelId);
				if (channel == null) {
					ignoreExc(() -> bot.getDBUtil().tickets.forceCloseTicket(channelId));
					return;
				}
				bot.getTicketUtil().closeTicket(channelId, null, "time", failure -> {
					ignoreExc(() -> db.tickets.setRequestStatus(channelId, -1L));
					if (ErrorResponse.UNKNOWN_MESSAGE.test(failure) || ErrorResponse.UNKNOWN_CHANNEL.test(failure)) return;
					log.error("Failed to delete ticket channel, either already deleted or unknown error", failure);
				});
			});

			db.tickets.getReplyExpiredTickets().forEach(channelId -> {
				GuildMessageChannel channel = bot.JDA.getChannelById(GuildMessageChannel.class, channelId);
				if (channel == null) {
					ignoreExc(() -> bot.getDBUtil().tickets.forceCloseTicket(channelId));
					return;
				}
				channel.getIterableHistory()
					.takeAsync(1)
					.thenAcceptAsync(list -> {
						Message msg = list.getFirst();
						if (msg.getAuthor().isBot()) {
							// Last message is bot - close ticket
							bot.getTicketUtil().closeTicket(channelId, null, "activity", failure -> {
								ignoreExc(() -> db.tickets.setWaitTime(channelId, -1L));
								if (ErrorResponse.UNKNOWN_MESSAGE.test(failure) || ErrorResponse.UNKNOWN_CHANNEL.test(failure)) return;
								log.error("Failed to delete ticket channel, either already deleted or unknown error", failure);
							});
						} else {
							// There is human reply
							ignoreExc(() -> db.tickets.setWaitTime(channelId, -1L));
						}
					});
			});
		} catch (Throwable t) {
			log.error("Exception caught during tickets checks.", t);
		}
	}

	private void checkExpiredTempRoles() {
		try {
			List<Map<String, Object>> expired = db.tempRoles.expiredRoles();
			if (expired.isEmpty()) return;

			expired.forEach(data -> {
				long roleId = castLong(data.get("roleId"));
				Role role = bot.JDA.getRoleById(roleId);
				if (role == null) {
					ignoreExc(() -> db.tempRoles.removeRole(roleId));
					return;
				}

				long userId = castLong(data.get("userId"));
				if (db.tempRoles.shouldDelete(roleId)) {
					try {
						role.delete().reason("Role expired").queue();
					} catch (InsufficientPermissionException | HierarchyException ex) {
						log.warn("Was unable to delete temporary role '{}' during scheduled check.", roleId, ex);
					}
					ignoreExc(() -> db.tempRoles.removeRole(roleId));
				} else {
					role.getGuild().removeRoleFromMember(User.fromId(userId), role).reason("Role expired").queue(null, failure -> {
						log.warn("Was unable to remove temporary role '{}' from '{}' during scheduled check.", roleId, userId, failure);
					});
					ignoreExc(() -> db.tempRoles.remove(roleId, userId));
				}
				// Log
				bot.getLogger().role.onTempRoleAutoRemoved(role.getGuild(), userId, role);
			});
		} catch (Throwable t) {
			log.error("Exception caught during expired roles check.", t);
		}
	}

	private void checkExpiredStrikes() {
		try {
			List<Map<String, Object>> expired = db.strikes.getExpired();
			if (expired.isEmpty()) return;

			for (Map<String, Object> data : expired) {
				Long guildId = castLong(data.get("guildId"));
				Long userId = castLong(data.get("userId"));
				Integer strikes = (Integer) data.get("count");

				if (strikes <= 0) {
					// Should not happen...
					db.strikes.removeGuildUser(guildId, userId);
				} else if (strikes == 1) {
					// One strike left, remove user
					db.strikes.removeGuildUser(guildId, userId);
					// set case inactive
					db.cases.setInactiveStrikeCases(userId, guildId);
				} else {
					String[] cases = ((String) data.getOrDefault("data", "")).split(";");
					// Update data
					if (!cases[0].isEmpty()) {
						String[] caseInfo = cases[0].split("-");
						String caseRowId = caseInfo[0];
						int newCount = Integer.parseInt(caseInfo[1]) - 1;

						StringBuilder newData = new StringBuilder();
						if (newCount > 0) {
							newData.append(caseRowId).append("-").append(newCount);
							if (cases.length > 1)
								newData.append(";");
						} else {
							// Set case inactive
							db.cases.setInactive(Integer.parseInt(caseRowId));
						}
						if (cases.length > 1) {
							List<String> list = new ArrayList<>(List.of(cases));
							list.removeFirst();
							newData.append(String.join(";", list));
						}
						// Remove one strike and reset time
						db.strikes.removeStrike(guildId, userId,
							Instant.now().plus(bot.getDBUtil().getGuildSettings(guildId).getStrikeExpires(), ChronoUnit.DAYS),
							1, newData.toString()
						);
					} else {
						db.strikes.removeGuildUser(guildId, userId);
						throw new Exception("Strike data is empty. Deleted data for gid '%s' and uid '%s'".formatted(guildId, userId));
					}
				}
			}
		} catch (Throwable t) {
			log.error("Exception caught during expired warns check.", t);
		}
	}

	private void checkExpiredPersistentRoles() {
		try {
			db.persistent.removeExpired();
		} catch (Throwable t) {
			log.error("Exception caught during expired persistent roles check.", t);
		}
	}

	private void generateReport() {
		try {
			List<Map<String, Object>> expired = db.modReport.getExpired(LocalDateTime.now());
			if (expired.isEmpty()) return;

			expired.forEach(data -> {
				long channelId = castLong(data.get("channelId"));
				TextChannel channel = bot.JDA.getTextChannelById(channelId);
				if (channel == null) {
					long guildId = castLong(data.get("guildId"));
					log.warn("Channel for modReport @ '{}' not found. Deleting.", guildId);
					try {
						db.modReport.removeGuild(guildId);
					} catch (SQLException ignored) {}
					return;
				}

				Guild guild = channel.getGuild();
				String[] roleIds = ((String) data.get("roleIds")).split(";");
				List<Role> roles = Stream.of(roleIds)
					.map(guild::getRoleById)
					.toList();
				if (roles.isEmpty()) {
					log.warn("Roles for modReport @ '{}' not found. Deleting.", guild.getId());
					try {
						db.modReport.removeGuild(guild.getIdLong());
					} catch (SQLException ignored) {}
					return;
				}

				int interval = (Integer) data.get("interval");
				LocalDateTime nextReport = LocalDateTime.ofEpochSecond(castLong(data.get("nextReport")), 0, ZoneOffset.UTC);
				nextReport = interval==30 ? nextReport.plusMonths(1) : nextReport.plusDays(interval);
				// Update next report date
				// If fails - remove guild
				try {
					db.modReport.updateNext(channelId, nextReport);
				} catch (SQLException ignored) {
					ignoreExc(() -> db.modReport.removeGuild(guild.getIdLong()));
				}

				// Search for members with any of required roles (Mod, Admin, ...)
				guild.findMembers(m -> !Collections.disjoint(m.getRoles(), roles)).setTimeout(10, TimeUnit.SECONDS).onSuccess(members -> {
					if (members.isEmpty() || members.size() > 20) return; // TODO normal reply - too much users
					LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
					LocalDateTime previous = (interval==30 ?
						now.minusMonths(1) :
						now.minusDays(interval)
					);

					List<ReportData> reportDataList = new ArrayList<>(members.size());
					members.forEach(m -> {
						if (m.getUser().isBot()) return;
						int countRoles = bot.getDBUtil().tickets.countTicketsByMod(guild.getIdLong(), m.getIdLong(), previous, now, true);
						Map<Integer, Integer> countCases = bot.getDBUtil().cases.countCasesByMod(guild.getIdLong(), m.getIdLong(), previous, now);
						ReportData reportData = new ReportData(m, countRoles, countCases);
						if (reportData.getCountTotalInt() > 0) {
							reportDataList.add(reportData);
						}
					});

					ModReportRender render = new ModReportRender(guild.getLocale(), bot.getLocaleUtil(),
						previous, now, reportDataList);

					final String attachmentName = EncodingUtil.encodeModreport(guild.getIdLong(), now.toEpochSecond(ZoneOffset.UTC));

					try {
						channel.sendFiles(FileUpload.fromData(
							new ByteArrayInputStream(render.renderToBytes()),
							attachmentName
						)).queue();
					} catch (IOException e) {
						log.error("Exception caught during rendering of modReport.", e);
					}
				});
			});
		} catch (Throwable t) {
			log.error("Exception caught during modReport schedule check.", t);
		}
	}

	// Each 2-5 minutes
	public void regularChecks() {
		CompletableFuture.runAsync(this::checkExpiredCases)
			.thenRunAsync(this::updateDbQueue);
	}

	private void checkExpiredCases() {
		List<CaseData> expired = db.cases.getExpired();
		if (expired.isEmpty()) return;
		
		expired.forEach(caseData -> {
			try {
				db.cases.setInactive(caseData.getRowId());
				if (caseData.getType().equals(BAN)) {
					Guild guild = bot.JDA.getGuildById(caseData.getGuildId());
					if (guild == null || !guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) return;
					guild.unban(User.fromId(caseData.getTargetId())).reason(bot.getLocaleUtil().getLocalized(guild.getLocale(), "misc.ban_expired")).queue(
						s -> bot.getLogger().mod.onAutoUnban(caseData, guild),
						f -> log.warn("Exception at unban attempt.", f)
					);
				}
			} catch (SQLException ignored) {}
		});
	}

	private void updateDbQueue() {
		try {
			// level data
			Iterator<PlayerObject> itr = bot.getLevelUtil().getUpdateQueue().iterator();
			int updatedCount = 0;
			while (itr.hasNext()) {
				PlayerObject player = itr.next();
				LevelManager.PlayerData playerData = db.levels.getPlayer(player);
				if (playerData == null) continue;

				db.levels.updatePlayer(player, playerData);
				itr.remove();
				updatedCount++;
			}
			if (updatedCount != 0) log.debug("Updated data for {} players", updatedCount);
		} catch (Throwable t) {
			log.error("Exception caught during DB queue update.", t);
		}
	}


	private void ignoreExc(RunnableExc runnable) {
		try {
			runnable.run();
		} catch (SQLException ignored) {}
	}

	@FunctionalInterface public interface RunnableExc { void run() throws SQLException; }
	
}
