package dev.fireatom.FABI.utils.database;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.utils.database.managers.*;
import dev.fireatom.FABI.utils.database.managers.GuildSettingsManager.GuildSettings;
import dev.fireatom.FABI.utils.database.managers.TicketSettingsManager.TicketSettings;
import dev.fireatom.FABI.utils.database.managers.VerifySettingsManager.VerifySettings;
import dev.fireatom.FABI.utils.database.managers.GuildLogsManager.LogSettings;
import dev.fireatom.FABI.utils.database.managers.GuildVoiceManager.VoiceSettings;
import dev.fireatom.FABI.utils.file.FileManager;

import net.dv8tion.jda.api.entities.Guild;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class DBUtil {

	private final FileManager fileManager;
	private final ConnectionUtil connectionUtil;
	
	protected final Logger log = (Logger) LoggerFactory.getLogger(DBUtil.class);

	public final GuildSettingsManager guildSettings;
	public final GuildVoiceManager guildVoice;
	public final UserSettingsManager user;
	public final VoiceChannelManager voice;
	public final WebhookManager webhook;
	public final AccessManager access;
	public final GroupManager group;
	public final VerifySettingsManager verifySettings;
	public final CaseManager cases;
	public final StrikeManager strikes;
	public final GuildLogsManager logs;
	public final LogExemptionsManager logExemptions;
	public final RoleManager roles;
	public final TempRoleManager tempRoles;
	public final TicketSettingsManager ticketSettings;
	public final TicketPanelManager ticketPanels;
	public final TicketTagManager ticketTags;
	public final TicketManager tickets;
	public final AutopunishManager autopunish;
	public final BlacklistManager blacklist;
	public final ModifyRoleManager modifyRole;
	public final GameStrikeManager games;
	public final PersistentManager persistent;
	public final ModReportManager modReport;
	public final LevelManager levels;
	public final LevelRolesManager levelRoles;

	public final BotBlacklistManager botBlacklist;

	public DBUtil(FileManager fileManager) {
		this.fileManager = fileManager;
		this.connectionUtil = new ConnectionUtil("jdbc:sqlite:"+fileManager.getFiles().get("database"), log);

		updateDB();
		
		guildSettings = new GuildSettingsManager(connectionUtil);
		access = new AccessManager(connectionUtil);
		group = new GroupManager(connectionUtil);
		guildVoice = new GuildVoiceManager(connectionUtil);
		voice = new VoiceChannelManager(connectionUtil);
		user = new UserSettingsManager(connectionUtil);
		webhook = new WebhookManager(connectionUtil);
		verifySettings = new VerifySettingsManager(connectionUtil);
		cases = new CaseManager(connectionUtil);
		strikes = new StrikeManager(connectionUtil);
		logs = new GuildLogsManager(connectionUtil);
		logExemptions = new LogExemptionsManager(connectionUtil);
		roles = new RoleManager(connectionUtil);
		tempRoles = new TempRoleManager(connectionUtil);
		ticketSettings = new TicketSettingsManager(connectionUtil);
		ticketPanels = new TicketPanelManager(connectionUtil);
		ticketTags = new TicketTagManager(connectionUtil);
		tickets = new TicketManager(connectionUtil);
		autopunish = new AutopunishManager(connectionUtil);
		blacklist = new BlacklistManager(connectionUtil);
		modifyRole = new ModifyRoleManager(connectionUtil);
		games = new GameStrikeManager(connectionUtil);
		persistent = new PersistentManager(connectionUtil);
		modReport = new ModReportManager(connectionUtil);
		levels = new LevelManager(connectionUtil);
		levelRoles = new LevelRolesManager(connectionUtil);

		botBlacklist = new BotBlacklistManager(connectionUtil);
	}

	public GuildSettings getGuildSettings(Guild guild) {
		return guildSettings.getSettings(guild.getIdLong());
	}

	public GuildSettings getGuildSettings(long guildId) {
		return guildSettings.getSettings(guildId);
	}

	public VerifySettings getVerifySettings(Guild guild) {
		return verifySettings.getSettings(guild.getIdLong());
	}

	public LogSettings getLogSettings(Guild guild) {
		return logs.getSettings(guild.getIdLong());
	}

	public TicketSettings getTicketSettings(Guild guild) {
		return ticketSettings.getSettings(guild.getIdLong());
	}

	public VoiceSettings getVoiceSettings(Guild guild) {
		return guildVoice.getSettings(guild.getIdLong());
	}

	// 0 - no version or error
	// 1> - compare active db version with resources
	// if version lower -> apply instruction for creating new tables, adding/removing columns
	// in the end set active db version to resources
	public int getActiveDBVersion() {
		int version = 0;
		try (Connection conn = DriverManager.getConnection(connectionUtil.getUrlSQLite());
			PreparedStatement st = conn.prepareStatement("PRAGMA user_version")) {
			version = st.executeQuery().getInt(1);
		} catch(SQLException ex) {
			log.warn("SQLite: Failed to get active database version", ex);
		}
		return version;
	}

	public int getResourcesDBVersion() {
		int version = 0;
		try {
			File tempFile = File.createTempFile("local-", ".tmp");
			if (!fileManager.export(getClass().getResourceAsStream("/server.db"), tempFile.toPath())) {
				log.error("Failed to write temp file {}!", tempFile.getName());
				return version;
			} else {
				try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempFile.getAbsolutePath());
				PreparedStatement st = conn.prepareStatement("PRAGMA user_version")) {
					version = st.executeQuery().getInt(1);
				} catch(SQLException ex) {
					log.warn("Failed to get resources database version", ex);
				}
			}
			boolean ignored = tempFile.delete();
		} catch (IOException ioException) {
			log.error("Exception at version check\n", ioException);
		}
		return version;
	}

	private List<List<String>> loadInstructions(Integer activeVersion) {
		List<String> lines = new ArrayList<>();
		try {
			File tempFile = File.createTempFile("database_updates", ".tmp");
			if (!fileManager.export(App.class.getResourceAsStream("/database_updates"), tempFile.toPath())) {
				log.error("Failed to write instruction temp file {}!", tempFile.getName());
			} else {
				lines = Files.readAllLines(tempFile.toPath(), StandardCharsets.UTF_8);
			}
		} catch (Exception ex) {
			log.error("SQLite: Failed to read update file", ex);
		}
		lines = lines.subList(activeVersion - 1, lines.size());
		List<List<String>> result = new ArrayList<>();
		lines.forEach(line -> {
			String[] points = line.split(";");
			List<String> list = points.length == 0 ? List.of(line) : List.of(points);
			result.add(list);
		});
		return result;
	}

	private void updateDB() {
		// 0 - skip
		int newVersion = getResourcesDBVersion();
		if (newVersion == 0) return;
		int activeVersion = getActiveDBVersion();
		if (activeVersion == 0) return;

		if (newVersion > activeVersion) {
			try (
				Connection conn = DriverManager.getConnection(connectionUtil.getUrlSQLite());
				Statement st = conn.createStatement()
			) {
				conn.setAutoCommit(false);
				try {
					for (List<String> version : loadInstructions(activeVersion)) {
						for (String sql : version) {
							log.debug(sql);
							st.execute(sql);
						}
						conn.commit();
					}
				} catch (SQLException ex) {
					conn.rollback();
					throw ex; // rethrow
				}
			} catch(SQLException ex) {
				log.error("SQLite: Failed to execute update!\nRollback performed. Continue database update manually.\n{}", ex.getMessage());
				return;
			}
			
			// Update version
			try (Connection conn = DriverManager.getConnection(connectionUtil.getUrlSQLite());
			Statement st = conn.createStatement()) {
				st.execute("PRAGMA user_version = "+newVersion);
				log.info("SQLite: Database version updated to {}", newVersion);
			} catch(SQLException ex) {
				log.error("SQLite: Failed to set active database version", ex);
			}
		}
	}

}
