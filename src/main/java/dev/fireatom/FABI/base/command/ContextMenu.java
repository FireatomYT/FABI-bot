/*
 * Copyright 2016-2018 John Grosh (jagrosh) & Kaidan Gustave (TheMonitorLizard)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fireatom.FABI.base.command;

import java.util.*;

import dev.fireatom.FABI.objects.CmdAccessLevel;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.NotNull;

/**
 * Middleware for child context menu types. Anything that extends this class will inherit the following options.
 *
 * @author Olivia (Chew)
 */
public abstract class ContextMenu extends Interaction
{
	/**
	 * The name of the command. This appears in the context menu.
	 * Can be 1-32 characters long. Spaces are allowed.
	 * @see CommandData#setName(String)
	 */
	@NotNull
	protected String name = "null";

	/**
	 * Gets the {@link ContextMenu ContextMenu.name} for the Context Menu.
	 *
	 * @return The name for the Context Menu.
	 */
	@NotNull
	public String getName()
	{
		return name;
	}

	/**
	 * Localization of menu names. Allows discord to change the language of the name of menu in the client.
	 */
	@NotNull
	protected Map<DiscordLocale, String> nameLocalization = new HashMap<>();

	/**
	 * Gets the specified localizations of menu name.
	 * @return Menu name localizations.
	 */
	@NotNull
	public Map<DiscordLocale, String> getNameLocalization() {
		return nameLocalization;
	}

	/**
	 * Gets the type of context menu.
	 *
	 * @return the type
	 */
	@NotNull
	public Command.Type getType()
	{
		if (this instanceof MessageContextMenu)
			return Command.Type.MESSAGE;
		else if (this instanceof UserContextMenu)
			return Command.Type.USER;
		else
			return Command.Type.UNKNOWN;
	}

	/**
	 * Gets the proper cooldown key for this Command under the provided {@link GenericCommandInteractionEvent}.
	 *
	 * @param event The ContextMenuEvent to generate the cooldown for.
	 *
	 * @return A String key to use when applying a cooldown.
	 */
	public String getCooldownKey(GenericCommandInteractionEvent event)
	{
		return switch (cooldownScope) {
			case USER -> cooldownScope.genKey(name, event.getUser().getIdLong());
			case USER_GUILD ->
				Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name, event.getUser().getIdLong(), g.getIdLong()))
					.orElse(CooldownScope.USER_CHANNEL.genKey(name, event.getUser().getIdLong(), event.getChannel().getIdLong()));
			case USER_CHANNEL ->
				cooldownScope.genKey(name, event.getUser().getIdLong(), event.getChannel().getIdLong());
			case GUILD -> Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name, g.getIdLong()))
				.orElse(CooldownScope.CHANNEL.genKey(name, event.getChannel().getIdLong()));
			case CHANNEL -> cooldownScope.genKey(name, event.getChannel().getIdLong());
			case SHARD ->
				event.getJDA().getShardInfo() != JDA.ShardInfo.SINGLE ? cooldownScope.genKey(name, event.getJDA().getShardInfo().getShardId()) :
					CooldownScope.GLOBAL.genKey(name, 0);
			case USER_SHARD ->
				event.getJDA().getShardInfo() != JDA.ShardInfo.SINGLE ? cooldownScope.genKey(name, event.getUser().getIdLong(), event.getJDA().getShardInfo().getShardId()) :
					CooldownScope.USER.genKey(name, event.getUser().getIdLong());
			case GLOBAL -> cooldownScope.genKey(name, 0);
		};
	}

	/**
	 * Gets an error message for this Context Menu under the provided {@link GenericCommandInteractionEvent}.
	 *
	 * @param  event
	 *         The event to generate the error message for.
	 * @param  guild
	 *         The guild where command is run.
	 * @param  remaining
	 *         The remaining number of seconds a context menu is on cooldown for.
	 *
	 * @return A String error message for this menu if {@code remaining > 0},
	 *         else {@code null}.
	 */
	public MessageCreateData getCooldownError(IReplyCallback event, Guild guild, int remaining) {
		if (remaining <= 0)
			return null;
		
		StringBuilder front = new StringBuilder(lu.getText(event, "errors.cooldown.cooldown_command")
			.replace("{time}", Integer.toString(remaining))
		);
		if (cooldownScope.equals(CooldownScope.USER_GUILD) && guild==null)
			front.append(" ").append(lu.getText(event, CooldownScope.USER_CHANNEL.getErrorPath()));
		else if (cooldownScope.equals(CooldownScope.GUILD) && guild==null)
			front.append(" ").append(lu.getText(event, CooldownScope.CHANNEL.getErrorPath()));
		else if (!cooldownScope.equals(CooldownScope.USER))
			front.append(" ").append(lu.getText(event, cooldownScope.getErrorPath()));
		
		return MessageCreateData.fromContent(Objects.requireNonNull(front.append("!").toString()));
	}

	/**
	 * Builds CommandData for the ContextMenu upsert.
	 * This code is executed when we need to upsert the menu.
	 * <p>
	 * Useful for manual upserting.
	 *
	 * @return the built command data
	 */
	public CommandData buildCommandData() {
		// Set attributes
		this.nameLocalization = lu.getFullLocaleMap(getPath()+".name", lu.getText(getPath()+".name"));

		// Make the command data
		CommandData data = Commands.context(getType(), name);

		//Check name localizations
		if (!getNameLocalization().isEmpty())
		{
			//Add localizations
			data.setNameLocalizations(getNameLocalization());
		}

		if (!isOwnerCommand() || getAccessLevel().isLowerThan(CmdAccessLevel.ADMIN))
			data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(getUserPermissions()));
		else
			data.setDefaultPermissions(DefaultMemberPermissions.DISABLED);

		data.setContexts(this.guildOnly ? Set.of(InteractionContextType.GUILD) : Set.of(InteractionContextType.GUILD, InteractionContextType.BOT_DM));

		return data;
	}
}
