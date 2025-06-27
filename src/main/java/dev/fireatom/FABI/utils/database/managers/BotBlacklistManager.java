package dev.fireatom.FABI.utils.database.managers;

import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BotBlacklistManager extends LiteBase {
	// Cache
	private final Set<Long> cache = Collections.synchronizedSet(new HashSet<>());

	public BotBlacklistManager(ConnectionUtil cu) {
		super(cu, "botBlacklist");
		loadCache();
	}

	public void add(long id) throws SQLException {
		cache.add(id);
		execute("INSERT INTO %s(id) VALUES (%s) ON CONFLICT (id) DO NOTHING".formatted(table, id));
	}

	public void remove(long id) throws SQLException {
		cache.remove(id);
		execute("DELETE FROM %s WHERE (id = %s)".formatted(table, id));
	}

	public boolean blacklisted(long id) {
		return cache.contains(id);
	}

	private void loadCache() {
		cache.addAll(select("SELECT id FROM %s".formatted(table), "id", Long.class));
	}
}
