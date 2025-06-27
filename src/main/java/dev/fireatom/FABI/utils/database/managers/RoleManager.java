package dev.fireatom.FABI.utils.database.managers;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.fireatom.FABI.objects.RoleType;
import dev.fireatom.FABI.utils.CastUtil;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;

public class RoleManager extends LiteBase {
	
	public RoleManager(ConnectionUtil cu) {
		super(cu, "roles");
	}

	public void add(long guildId, long roleId, String description, Integer row, RoleType roleType, boolean timed) throws SQLException {
		execute("INSERT INTO %s(guildId, roleId, description, type, row, timed) VALUES (%s, %s, %s, %s, %s, %s)"
			.formatted(table, guildId, roleId, quote(description), roleType.getType(), Optional.ofNullable(row).orElse(0), timed ? 1 : 0));
	}

	public void remove(long roleId) throws SQLException {
		execute("DELETE FROM %s WHERE (roleId=%s)".formatted(table, roleId));
	}

	public void removeAll(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public List<RoleData> getRolesByType(long guildId, RoleType type) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)"
			.formatted(table, guildId, type.getType()), Set.of("roleId", "description"));
		return data.stream().map(map -> new RoleData(map, type)).toList();
	}

	public List<RoleData> getAssignable(long guildId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)"
			.formatted(table, guildId, RoleType.ASSIGN.getType()), Set.of("roleId", "description", "row", "timed"));
		return data.stream().map(map -> new RoleData(map, RoleType.ASSIGN)).toList();
	}

	public List<RoleData> getAssignableByRow(long guildId, int row) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%s AND type=%d AND row=%d)"
			.formatted(table, guildId, RoleType.ASSIGN.getType(), row), Set.of("roleId", "description", "timed"));
		return data.stream().map(map -> new RoleData(map, RoleType.ASSIGN)).toList();
	}

	public List<RoleData> getToggleable(long guildId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)"
			.formatted(table, guildId, RoleType.TOGGLE.getType()), Set.of("roleId", "description"));
		return data.stream().map(map -> new RoleData(map, RoleType.TOGGLE)).toList();
	}

	public List<RoleData> getCustom(long guildId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%s AND type=%d)"
			.formatted(table, guildId, RoleType.CUSTOM.getType()), Set.of("roleId", "description"));
		return data.stream().map(map -> new RoleData(map, RoleType.CUSTOM)).toList();
	}

	public int getRowSize(long guildId, int row) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND row=%d)".formatted(table, guildId, row));
	}

	public int countRoles(long guildId, RoleType type) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s AND type=%d)".formatted(table, guildId, type.getType()));
	}

	public RoleType getType(long roleId) {
		Integer data = selectOne("SELECT type FROM %s WHERE (roleId=%s)".formatted(table, roleId), "type", Integer.class);
		if (data == null) return null;
		return RoleType.byType(data);
	}

	public String getDescription(long roleId) {
		return selectOne("SELECT description FROM %s WHERE (roleId=%s)".formatted(table, roleId), "description", String.class);
	}

	public void setDescription(long roleId, String description) throws SQLException {
		execute("UPDATE %s SET description=%s WHERE (roleId=%s)".formatted(table, quote(description), roleId));
	}

	public void setRow(long roleId, Integer row) throws SQLException {
		execute("UPDATE %s SET row=%d WHERE (roleId=%s)".formatted(table, Optional.ofNullable(row).orElse(0), roleId));
	}

	public void setTimed(long roleId, boolean timed) throws SQLException {
		execute("UPDATE %s SET timed=%s WHERE (roleId=%s)".formatted(table, timed ? 1 : 0, roleId));
	}

	public boolean isToggleable(long roleId) {
		RoleType type = getType(roleId);
		return type != null && type.equals(RoleType.TOGGLE);
	}

	public boolean isTemp(long roleId) {
		Integer data = selectOne("SELECT timed FROM %s WHERE (roleId=%s)".formatted(table, roleId), "timed", Integer.class);
		return data != null && data == 1;
	}

	public boolean existsRole(long roleId) {
		return selectOne("SELECT roleId FROM %s WHERE (roleId=%s)".formatted(table, roleId), "roleId", Long.class) != null;
	}

	public static class RoleData {
		private final long roleId;
		private final RoleType type;
		private final int row;
		private final String description;
		private final boolean isTimed;

		public RoleData(Map<String, Object> map, RoleType type) {
			this.roleId = CastUtil.castLong(map.get("roleId"));
			this.type = type;
			this.row = CastUtil.getOrDefault(map.get("row"), 0);
			this.description = CastUtil.getOrDefault(map.get("description"), null);
			this.isTimed = CastUtil.getOrDefault(map.get("timed"), 0) == 1;
		}

		public long getIdLong() {
			return roleId;
		}

		public String getId() {
			return String.valueOf(roleId);
		}

		public RoleType getType() {
			return type;
		}

		public Integer getRow() {
			return row;
		}

		public String getDescription(String defaultValue) {
			return description==null ? defaultValue : description;
		}

		public boolean isTimed() {
			return isTimed;
		}
	}
}
