package dev.fireatom.FABI.utils.database.managers;

import static dev.fireatom.FABI.utils.CastUtil.getOrDefault;
import static dev.fireatom.FABI.utils.CastUtil.resolveOrDefault;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.FixedCache;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;
import org.jetbrains.annotations.Nullable;

public class VerifySettingsManager extends LiteBase {

	private final Set<String> columns = Set.of("roleId", "panelText", "panelImage", "additionalRoles");

	// Cache
	private final FixedCache<Long, VerifySettings> cache = new FixedCache<>(Constants.DEFAULT_CACHE_SIZE);
	private final VerifySettings blankSettings = new VerifySettings();

	public VerifySettingsManager(ConnectionUtil cu) {
		super(cu, "verifySettings");
	}

	public VerifySettings getSettings(long guildId) {
		if (cache.contains(guildId))
			return cache.get(guildId);
		VerifySettings settings = applyNonNull(getData(guildId), VerifySettings::new);
		if (settings == null)
			settings = blankSettings;
		cache.put(guildId, settings);
		return settings;
	}

	private Map<String, Object> getData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), columns);
	}

	public void remove(long guildId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public void setVerifyRole(long guildId, long roleId) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, roleId) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET roleId=%<d".formatted(table, guildId, roleId));
	}

	public void setPanelText(long guildId, String text) throws SQLException {
		invalidateCache(guildId);
		final String textParsed = quote(text.replace("\\n", "<br>"));
		execute("INSERT INTO %s(guildId, panelText) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET panelText=%<s".formatted(table, guildId, textParsed));
	}

	public void setPanelImage(long guildId, String imageUrl) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, panelImage) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET panelImage=%<s".formatted(table, guildId, quote(imageUrl)));
	}

	public void setAdditionalRoles(long guildId, @Nullable String roleIds) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, additionalRoles) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET additionalRoles=%<s".formatted(table, guildId, quote(roleIds)));
	}

	private void invalidateCache(long guildId) {
		cache.pull(guildId);
	}

	public static class VerifySettings {
		private final Long roleId;
		private final String panelText, panelImageUrl;
		private final Set<Long> additionalRoles;

		public VerifySettings() {
			this.roleId = null;
			this.panelText = null;
			this.panelImageUrl = null;
			this.additionalRoles = Set.of();
		}

		public VerifySettings(Map<String, Object> data) {
			this.roleId = getOrDefault(data.get("roleId"), null);
			this.panelText = getOrDefault(data.get("panelText"), null);
			this.panelImageUrl = getOrDefault(data.get("panelImage"), null);
			this.additionalRoles = resolveOrDefault(
				data.get("additionalRoles"),
				obj->Stream.of(String.valueOf(obj).split(";"))
					.map(Long::parseLong)
					.collect(Collectors.toSet()),
				Set.of()
			);
		}

		public Long getRoleId() {
			return roleId;
		}

		public String getPanelText() {
			return panelText;
		}

		public String getPanelImageUrl() {
			return panelImageUrl;
		}

		public Set<Long> getAdditionalRoles() {
			return additionalRoles;
		}
	}
	
}
