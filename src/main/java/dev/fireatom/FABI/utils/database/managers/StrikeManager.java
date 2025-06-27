package dev.fireatom.FABI.utils.database.managers;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;

import net.dv8tion.jda.internal.utils.tuple.Pair;

public class StrikeManager extends LiteBase {

	public StrikeManager(ConnectionUtil cu) {
		super(cu, "strikeExpire");
	}

	public void addStrikes(long guildId, long userId, Instant expiresAt, int count, String caseInfo) throws SQLException {
		execute("INSERT INTO %s(guildId, userId, expiresAt, count, data, lastAddition) VALUES (%d, %d, %d, %d, %s, %s) ON CONFLICT(guildId, userId) DO UPDATE SET count=count+%5$d, data=data || ';' || %6$s, lastAddition=%7$s"
			.formatted(table, guildId, userId, expiresAt.getEpochSecond(), count, quote(caseInfo), Instant.now().getEpochSecond()));
	}

	public Integer getStrikeCount(long guildId, long userId) {
		return selectOne("SELECT count FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId), "count", Integer.class);
	}

	public List<Map<String, Object>> getExpired() {
		return select("SELECT * FROM %s WHERE (expiresAt<%d)".formatted(table, Instant.now().getEpochSecond()), Set.of("guildId", "userId", "count", "data"));
	}

	public Pair<Integer, String> getData(long guildId, long userId) {
		Map<String, Object> data = selectOne("SELECT count, data FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId), Set.of("count", "data"));
		if (data == null || data.isEmpty()) return null;
		return Pair.of((Integer) data.get("count"), String.valueOf(data.getOrDefault("data", "")));
	}

	public Pair<Integer, Integer> getDataCountAndDate(long guildId, long userId) {
		Map<String, Object> data = selectOne("SELECT count, expiresAt FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId), Set.of("count", "expiresAt"));
		if (data == null || data.isEmpty()) return null;
		return Pair.of((Integer) data.get("count"), (Integer) data.get("expiresAt"));
	}

	public void removeStrike(long guildId, long userId, Instant expiresAt, int amount, String newData) throws SQLException {
		execute("UPDATE %s SET expiresAt=%d, count=count-%d, data=%s WHERE (guildId=%d AND userId=%d)".formatted(table, expiresAt.getEpochSecond(), amount, quote(newData), guildId, userId));
	}

	public void removeGuildUser(long guildId, long userId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId));
	}

	public void removeGuild(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}
	
	public Instant getLastAddition(long guildId, long userId) {
		Long data = selectOne("SELECT lastAddition FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table, guildId, userId), "lastAddition", Long.class);
		return data==null ? null : Instant.ofEpochSecond(data);
	}
}
