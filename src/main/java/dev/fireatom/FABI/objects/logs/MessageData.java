package dev.fireatom.FABI.objects.logs;

import java.util.List;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import com.github.difflib.text.DiffRow.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MessageData {
	private final String content, authorName;
	private final Attachment attachment;
	private final long authorId;

	public MessageData(Message message) {
		this.content = message.getContentRaw();
		if (message.getAttachments().isEmpty())
			this.attachment = null;
		else
			this.attachment = message.getAttachments().getFirst();
		this.authorId = message.getAuthor().getIdLong();
		this.authorName = message.getAuthor().getName();
	}

	public String getContent() {
		return content;
	}

	public String getContentStripped() {
		return MarkdownSanitizer.sanitize(content);
	}

	public String getContentEscaped() {
		return MarkdownSanitizer.escape(content);
	}

	public Attachment getAttachment() {
		return attachment;
	}

	public long getAuthorId() {
		return authorId;
	}

	public String getAuthorName() {
		return authorName;
	}

	public boolean isEmpty() {
		return content.isBlank() && attachment == null;
	}

	@Nullable
	public static DiffData getDiffContent(@NotNull String oldContent, @NotNull String newContent) {
		if (oldContent.equals(newContent)) return null;
		DiffRowGenerator generator = DiffRowGenerator.create()
			.showInlineDiffs(true)
			.inlineDiffByWord(true)
			.ignoreWhiteSpaces(true)
			.lineNormalizer(f -> f)
			.newTag(f -> "")
			.oldTag(f -> "")
			.build();
		List<DiffRow> rows = generator.generateDiffRows(
			List.of(oldContent.split("\\n")),
			List.of(newContent.split("\\n"))
		);
		
		StringBuilder diff = new StringBuilder();
		int linesChanged = 0;
		int charsChanged = 0;
		boolean skipped = false;
		final int size = rows.size();
		for (int i = 0; i<size; i++) {
			DiffRow row = rows.get(i);
			if (row.getTag().equals(Tag.EQUAL)) {
				if ((i+1 >= size || rows.get(i+1).getTag().equals(Tag.EQUAL))
					&& (i-1 < 0 || rows.get(i-1).getTag().equals(Tag.EQUAL)))
				{
					skipped = true;
					continue;
				}
			}
			if (skipped) {
				diff.append(" ...\n");
				skipped = false;
			}

			if (!row.getTag().equals(Tag.EQUAL)) {
				linesChanged++;
				charsChanged += Math.max(row.getOldLine().length(), row.getNewLine().length());
			}
			switch (row.getTag()) {
				case INSERT -> diff.append("+ ").append(row.getNewLine());
				case DELETE -> diff.append("- ").append(row.getOldLine());
				case CHANGE -> diff.append("- ").append(row.getOldLine())
					.append("\n")
					.append("+ ").append(row.getNewLine());
				default -> diff.append(" ").append(row.getOldLine());
			}
			diff.append("\n");
		}
		return new DiffData(diff.toString(), linesChanged, charsChanged);
	}

	public record DiffData(@NotNull String content, int linesChanged, int charsChanged) {
		public boolean manyChanges() {
			return linesChanged > 3 || charsChanged > 600;
		}
	}
}
