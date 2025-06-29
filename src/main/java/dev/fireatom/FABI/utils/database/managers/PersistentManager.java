package dev.fireatom.FABI.utils.database.managers;

import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.CastUtil;
import dev.fireatom.FABI.utils.FixedCache;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PersistentManager extends LiteBase {
	private final String table_role = "persistentRole";
	private final String table_return = "returnRole";

	// Cache
	private final FixedCache<Long, List<Long>> roleCache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE); // GuildId - Role Ids
	private final FixedCache<Long, Map<Long, List<Long>>> returnCache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE); // GuildId - UserId and Role Ids //

	public PersistentManager(ConnectionUtil cu) {
		super(cu, null);
	}

	public void addRole(long guildId, long roleId) throws SQLException {
		invalidateRoleCache(guildId);
		execute("INSERT INTO %s(guildId, roleId) VALUES (%d, %d)".formatted(table_role, guildId, roleId));
	}

	public void removeRole(long guildId, long roleId) throws SQLException {
		invalidateRoleCache(guildId);
		execute("DELETE FROM %s WHERE (roleId = %d)".formatted(table_role, roleId));
	}

	public List<Long> getRoles(long guildId) {
		if (roleCache.contains(guildId))
			return roleCache.get(guildId);
		List<Long> data = getRolesData(guildId);
		if (data.isEmpty())
			data = List.of();
		roleCache.put(guildId, data);
		return data;
	}

	private List<Long> getRolesData(long guildId) {
		return select("SELECT roleId FROM %s WHERE (guildId=%d)".formatted(table_role, guildId), "roleId", Long.class);
	}

	public void addUser(long guildId, long userId, List<Long> roleIds) throws SQLException {
		// Add to cache
		Map<Long, List<Long>> data = getUsers(guildId);
		data.put(guildId, roleIds);
		// Add to db
		final String text = roleIds.stream().map(String::valueOf).collect(Collectors.joining(";"));
		execute("INSERT INTO %s(guildId, userId, roleIds, expiresAfter) VALUES (%d, %d, %s, %d) ON CONFLICT(guildId, userId) DO UPDATE SET roleIds=%4$s, expiresAfter=%5$d"
			.formatted(table_return, guildId, userId, quote(text), Instant.now().plus(Duration.ofDays(30)).getEpochSecond()));
	}

	public List<Long> getUserRoles(long guildId, long userId) throws SQLException {
		// Get and remove from cache
		Map<Long, List<Long>> data = getUsers(guildId);
		if (data.isEmpty() || !data.containsKey(userId))
			return List.of();

		List<Long> roleIds = data.get(userId);
		data.remove(userId);
		// Remove from bd
		execute("DELETE FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table_return, guildId, userId));
		// Return
		return Collections.unmodifiableList(roleIds);
	}

	public void removeExpired() {
		List<Map<String, Object>> data = select("SELECT guildId, userId FROM %s WHERE (expiresAfter<=%d)"
			.formatted(table_return, Instant.now().getEpochSecond()), Set.of("guildId", "userId"));
		if (data.isEmpty()) return;
		for (Map<String, Object> row : data) {
			long guildId = CastUtil.castLong(row.get("guildId"));
			long userId = CastUtil.castLong(row.get("userId"));
			// Remove from cache
			Map<Long, List<Long>> guildCache = getUsers(guildId);
			guildCache.remove(userId);
			// Remove from bd
			try {
				execute("DELETE FROM %s WHERE (guildId=%d AND userId=%d)".formatted(table_return, guildId, userId));
			} catch (SQLException ignored) {}
		}
	}

	private Map<Long, List<Long>> getUsers(long guildId) {
		if (returnCache.contains(guildId))
			return returnCache.get(guildId);
		Map<Long, List<Long>> data = new HashMap<>(getUsersData(guildId));
		returnCache.put(guildId, data);
		return data;
	}

	private Map<Long, List<Long>> getUsersData(long guildId) {
		List<Map<String, Object>> data = select("SELECT userId, roleIds FROM %s WHERE (guildId=%d)".formatted(table_return, guildId), Set.of("userId", "roleIds"));
		if (data.isEmpty()) return Map.of();
		return data.stream().collect(Collectors.toMap(
			m -> (Long) m.get("userId"),
			m -> Stream.of(String.valueOf(m.get("roleIds")).split(";")).map(Long::parseLong).toList()
		));
	}

	public void removeGuild(long guildId) throws SQLException {
		invalidateRoleCache(guildId);
		invalidateReturnCache(guildId);
		execute("DELETE FROM %1$s WHERE (guildId = %3$d); DELETE FROM %2$s WHERE (guildId = %3$d);"
			.formatted(table_role, table_return, guildId));
	}

	private void invalidateRoleCache(long guildId) {
		roleCache.pull(guildId);
	}

	private void invalidateReturnCache(long guildId) {
		returnCache.pull(guildId);
	}

}
