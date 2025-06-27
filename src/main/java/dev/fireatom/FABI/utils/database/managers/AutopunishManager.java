package dev.fireatom.FABI.utils.database.managers;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.fireatom.FABI.objects.PunishAction;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;

import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.Nullable;

public class AutopunishManager extends LiteBase {
	
	public AutopunishManager(ConnectionUtil cu) {
		super(cu, "autopunish");
	}

	public void addAction(long guildId, int atStrikeCount, List<PunishAction> actions, @Nullable String data) throws SQLException {
		execute("INSERT INTO %s(guildId, strike, actions, data) VALUES (%d, %d, %d, %s)".formatted(table, guildId, atStrikeCount, PunishAction.encodeActions(actions), quote(data)));
	}

	public void removeAction(long guildId, int atStrikeCount) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d AND strike=%d)".formatted(table, guildId, atStrikeCount));
	}

	public void removeGuild(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public Pair<Integer, String> getAction(long guildId, int atStrikeCount) {
		Map<String, Object> data = selectOne("SELECT actions, data FROM %s WHERE (guildId=%d AND strike=%d) ORDER BY strike DESC"
			.formatted(table, guildId, atStrikeCount), Set.of("actions", "data"));
		if (data == null) return null;
		return Pair.of((Integer) data.get("actions"), (String) data.getOrDefault("data", ""));
	}

	public Pair<Integer, String> getTopAction(long guildId, int minStrikeCount) {
		Map<String, Object> data = selectOne("SELECT actions, data FROM %s WHERE (guildId=%d AND strike<=%d) ORDER BY strike DESC"
			.formatted(table, guildId, minStrikeCount), Set.of("actions", "data"));
		if (data == null) return null;
		return Pair.of((Integer) data.get("actions"), (String) data.getOrDefault("data", ""));
	}

	public List<Autopunish> getAllActions(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), Set.of("strike", "actions", "data"))
			.stream()
			.map(Autopunish::new)
			.toList();
	}

	public static class Autopunish {
		private final int strike;
		private final List<PunishAction> actions;
		private final String data;

		public Autopunish(Map<String, Object> data) {
			this.strike = (Integer) data.get("strike");
			this.actions = PunishAction.decodeActions((Integer) data.get("actions"));
			this.data = (String) data.get("data");
		}

		public int getCount() {
			return strike;
		}

		public List<PunishAction> getActions() {
			return actions;
		}

		public String getData() {
			return data;
		}
	}
}
