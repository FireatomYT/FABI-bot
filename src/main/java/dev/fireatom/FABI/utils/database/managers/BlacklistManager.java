package dev.fireatom.FABI.utils.database.managers;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public class BlacklistManager extends LiteBase {
	
	public BlacklistManager(ConnectionUtil cu) {
		super(cu, "blacklist");
	}

	public void add(long guildId, int groupId, long userId, @Nullable String reason, long modId) throws SQLException {
		execute("INSERT INTO %s(guildId, groupId, userId, reason, modId) VALUES (%s, %s, %s, %s, %s)"
			.formatted(table, guildId, groupId, userId, quote(reason), modId));
	}

	public boolean inGroupUser(int groupId, long userId) {
		return selectOne("SELECT userId FROM %s WHERE (groupId=%d AND userId=%d)".formatted(table, groupId, userId), "userId", Long.class) != null;
	}

	public void removeUser(int groupId, long userId) throws SQLException {
		execute("DELETE FROM %s WHERE (groupId=%d AND userId=%d)".formatted(table, groupId, userId));
	}

	public Map<String, Object> getInfo(int groupId, long userId) {
		return selectOne("SELECT * FROM %s WHERE (groupId=%d AND userId=%d)".formatted(table, groupId, userId), Set.of("guildId", "reason", "modId"));
	}

	public List<Map<String, Object>> getByPage(int groupId, int page) {
		return select("SELECT * FROM %s WHERE (groupId=%d) ORDER BY userId DESC LIMIT 20 OFFSET %d".formatted(table, groupId, (page-1)*20), Set.of("guildId", "userId", "reason", "modId"));
	}

	public Integer countEntries(int groupId) {
		return count("SELECT COUNT(*) FROM %s WHERE (groupId=%d)".formatted(table, groupId));
	}
}
