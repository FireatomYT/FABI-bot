package dev.fireatom.FABI.utils.database.managers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;

import net.dv8tion.jda.api.EmbedBuilder;

import static dev.fireatom.FABI.utils.CastUtil.getOrDefault;
import static dev.fireatom.FABI.utils.CastUtil.requireNonNull;

public class TicketPanelManager extends LiteBase {
	
	public TicketPanelManager(ConnectionUtil cu) {
		super(cu, "ticketPanel");
	}

	public int createPanel(long guildId, String title, String description, String imageUrl, String footer) {
		List<String> keys = new ArrayList<>(5);
		List<String> values = new ArrayList<>(5);
		keys.add("guildId");
		values.add(String.valueOf(guildId));
		keys.add("title");
		values.add(quote(title));
		if (description != null) {
			keys.add("description");
			values.add(replaceNewline(description));
		}
		if (imageUrl != null) {
			keys.add("image");
			values.add(quote(imageUrl));
		}
		if (footer != null) {
			keys.add("footer");
			values.add(replaceNewline(footer));
		}
		return executeWithRow("INSERT INTO %s(%s) VALUES (%s)".formatted(table, String.join(", ", keys), String.join(", ", values)));
	}

	public void delete(int panelId) throws SQLException {
		execute("DELETE FROM %s WHERE (panelId=%d)".formatted(table, panelId));
	}

	public void deleteAll(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public Long getGuildId(int panelId) {
		return selectOne("SELECT guildId FROM %s WHERE (panelId=%d)".formatted(table, panelId), "guildId", Long.class);
	}

	public void updatePanel(int panelId, String title, String description, String imageUrl, String footer) throws SQLException {
		List<String> values = new ArrayList<>();
		if (title != null)
			values.add("title="+quote(title));
		if (description != null)
			values.add("description="+replaceNewline(description));
		if (imageUrl != null)
			values.add("image="+quote(imageUrl));
		if (footer != null)
			values.add("footer="+replaceNewline(footer));

		if (!values.isEmpty())
			execute("UPDATE %s SET %s WHERE (panelId=%d)".formatted(table, String.join(", ", values), panelId));
	}

	public Panel getPanel(int panelId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (panelId=%d)".formatted(table, panelId),
			Set.of("title", "description", "image", "footer"));
		if (data==null) return null;
		return new Panel(data);
	}

	public String getPanelTitle(int panelId) {
		return selectOne("SELECT title FROM %s WHERE (panelId=%d)".formatted(table, panelId), "title", String.class);
	}

	public Map<Integer, String> getPanelsText(long guildId) {
		List<Map<String, Object>> data = select("SELECT panelId, title FROM %s WHERE (guildId=%s)".formatted(table, guildId), Set.of("panelId", "title"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().limit(25).collect(Collectors.toMap(s -> (Integer) s.get("panelId"), s -> (String) s.get("title")));
	}

	public int countPanels(long guildId) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	// Tools
	private String replaceNewline(final String text) {
		return quote(text).replace("\\n", "<br>");
	}

	public static class Panel {
		private final String title, description, imageUrl, footer;

		public Panel(Map<String, Object> map) {
			this.title = requireNonNull(map.get("title"));
			this.description = setNewline(getOrDefault(map.get("description"), null));
			this.imageUrl = getOrDefault(map.get("image"), null);
			this.footer = setNewline(getOrDefault(map.get("footer"), null));
		}
		
		private String setNewline(String text) {
			if (text==null) return null;
			return text.replaceAll("<br>", "\n");
		}

		public EmbedBuilder getFilledEmbed(final int color) {
			EmbedBuilder builder = new EmbedBuilder()
				.setColor(color)
				.setTitle(title);
			if (description!=null) builder.setDescription(description);
			if (imageUrl!=null) builder.setImage(imageUrl);
			if (footer!=null) builder.setFooter(footer);
			return builder; 
		}
	}

}
