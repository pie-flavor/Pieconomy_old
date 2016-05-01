package flavor.pie;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.command.*;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.game.state.*;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.transaction.TransferResult;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextTemplate;

import com.google.common.base.Joiner;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Injector;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;

@Plugin(id="pieconomy",name="Pieconomy", authors="pie_flavor", description="An economy plugin that uses items as currency", version="1.1.0")
public class Pieconomy {
	@Inject Game game;
	@Inject @ConfigDir(sharedRoot = false) File dir;
	@Inject Logger logger;
	@Inject Injector injector;
	File currencyFile;
	File itemFile;
	HoconConfigurationLoader currencyLoader;
	HoconConfigurationLoader itemLoader;
	CommentedConfigurationNode currencyRoot;
	CommentedConfigurationNode itemRoot;
	PieconomyService service;
	HashMap<ItemType, Pair<BigDecimal, Currency>> items;
	@Listener
	public void onPreInit(GamePreInitializationEvent e) {
		try {
			itemFile = new File(dir, "items.conf");
			currencyFile = new File(dir, "currencies.conf");
			if (!itemFile.exists()) itemFile.createNewFile();
			if (!currencyFile.exists()) currencyFile.createNewFile();
			currencyLoader = HoconConfigurationLoader.builder().setFile(currencyFile).setDefaultOptions(ConfigurationOptions.defaults().setShouldCopyDefaults(false)).build();
			itemLoader = HoconConfigurationLoader.builder().setFile(itemFile).setDefaultOptions(ConfigurationOptions.defaults().setShouldCopyDefaults(false)).build();
			currencyRoot = currencyLoader.load();
			itemRoot = itemLoader.load();
			if (currencyRoot.getNode("version").isVirtual()) {
				currencyLoader.save(HoconConfigurationLoader.builder().setURL(getClass().getResource("currencies.conf")).build().load());
			}
			if (itemRoot.getNode("version").isVirtual()) {
				itemLoader.save(HoconConfigurationLoader.builder().setURL(getClass().getResource("items.conf")).build().load());
			}
			Currency defaultCurrency = null;
			Set<Currency> currencies = new HashSet<>();
			for (Entry<Object, ? extends CommentedConfigurationNode> entry : currencyRoot.getNode("currencies").getChildrenMap().entrySet()) {
				Object obj = entry.getKey();
				CommentedConfigurationNode node = entry.getValue();
				try {
					PieconomyCurrency currency = parse(obj, node, currencyRoot.getNode("default-currency").getString());
					currencies.add(currency);
					if (currency.isDefault()) {
						defaultCurrency = currency;
					}
				} catch (IllegalArgumentException ex) {
					logger.error("Error when loading config: "+ex.getMessage());
					logger.error("Disabling.");
					disable();
					return;
				}
			}
			if (defaultCurrency == null) {
				logger.error("Error when loading config: No default currency specified.");
				logger.error("Disabling.");
				disable();
				return;
			}
			currencies.forEach(c -> game.getRegistry().register(Currency.class, c));
			service = new PieconomyService(defaultCurrency, currencies, game, this);
			items = new HashMap<>();
			for (Entry<Object, ? extends CommentedConfigurationNode> entry : itemRoot.getNode("items").getChildrenMap().entrySet()) {
				Object obj = entry.getKey();
				ConfigurationNode node = entry.getValue();
				if (!(obj instanceof String)) {
					logger.error("Error when loading config: "+Joiner.on(".").join(node.getPath())+": Only string identifiers accepted");
					logger.error("Disabling.");
					disable();
					return;
				}
				String s = (String) obj;
				Optional<ItemType> type_ = game.getRegistry().getType(ItemType.class, s);
				if (!type_.isPresent()) {
					logger.error("Error when loading config: "+Joiner.on(".").join(node.getPath())+": Invalid ItemType");
					logger.error("Disabling.");
					disable();
					return;
				}
				ItemType type = type_.get();
				String currencyId = node.getNode("currency").getString(defaultCurrency.getId());
				Optional<Currency> currency_ = game.getRegistry().getType(Currency.class, currencyId);
				if (!currency_.isPresent()) {
					logger.error("Error when loading config: "+Joiner.on(".").join(node.getPath())+": Invalid ItemType");
					logger.error("Disabling.");
					disable();
					return;
				}
				Currency currency = currency_.get();
				long amount = node.getNode("amount").getLong(0);
				items.put(type, Pair.of(new BigDecimal(amount), currency));
			}
		} catch (IOException ex) {
			logger.error("Could not perform config operations! Disabling.");
			ex.printStackTrace();
			disable();
		}
	}
	@Listener
	public void onInit(GameInitializationEvent e) {
		game.getServiceManager().setProvider(this, EconomyService.class, service);
	}
	@Listener
	public void onPostInit(GamePostInitializationEvent e) {
		CommandSpec bal = CommandSpec.builder()
				.executor(this::bal)
				.description(Text.of("Tells you how much money you have on you."))
				.arguments(GenericArguments.optional(GenericArguments.user(Text.of("player"))),
						GenericArguments.optional(GenericArguments.catalogedElement(Text.of("currency"), Currency.class)))
				.build();
		game.getCommandManager().register(this, bal, "balance", "bal", "money");
		CommandSpec pay = CommandSpec.builder()
				.executor(this::pay)
				.description(Text.of("Pays a user some money."))
				.arguments(GenericArguments.user(Text.of("player")),
						GenericArguments.doubleNum(Text.of("amount")),
						GenericArguments.optional(GenericArguments.catalogedElement(Text.of("currency"), Currency.class)))
				.build();
		game.getCommandManager().register(this, pay, "pay", "transfer");
		CommandSpec value = CommandSpec.builder()
				.executor(this::value)
				.description(Text.of("Gets the value of an item."))
				.arguments(GenericArguments.optional(GenericArguments.catalogedElement(Text.of("item"), ItemType.class)))
				.build();
		game.getCommandManager().register(this, value, "value", "worth", "val");
	}
	CommandResult value(CommandSource src, CommandContext args) throws CommandException {
		Optional<ItemType> item_ = args.getOne("item");
		ItemType item;
		if (item_.isPresent()) {
			item = item_.get();
		} else {
			if (src instanceof Player) {
				Player p = (Player) src;
				Optional<ItemStack> stack = p.getItemInHand();
				if (stack.isPresent()) {
					item = stack.get().getItem();
				} else {
					throw new CommandException(Text.of("You must be holding an item!"));
				}
			} else {
				throw new CommandException(Text.of("You must specify an item type!"));
			}
		}
		Pair<BigDecimal, Currency> pair = items.get(item);
		if (pair == null) throw new CommandException(Text.of("This item is not worth anything!"));
		Text text = pair.getValue().format(pair.getKey());
		src.sendMessage(Text.of(item.getId(), " is worth ", text, "."));
		return CommandResult.builder().queryResult(pair.getKey().intValue()).successCount(1).build();
	}
	CommandResult bal(CommandSource src, CommandContext args) throws CommandException {
		Optional<User> user_ = args.getOne("player");
		User user;
		if (!user_.isPresent()) {
			if (src instanceof User)
				user = (User) src;
			else
				throw new CommandException(Text.of("You must specify a user!"));
		} else {
			user = user_.get();
		}
		EconomyService service = game.getServiceManager().provide(EconomyService.class).get();
		Currency currency;
		Optional<Currency> currency_ = args.getOne("currency");
		if (currency_.isPresent()) {
			currency = currency_.get();
		} else {
			currency = service.getDefaultCurrency();
		}
		Account acc = service.getOrCreateAccount(user.getUniqueId()).get();
		BigDecimal amount = acc.getBalance(currency);
		Text money = currency.format(amount);
		src.sendMessage(Text.of("You currently have ", money, "."));
		return CommandResult.builder().queryResult(amount.intValue()).successCount(1).build();
	}
	CommandResult pay(CommandSource src, CommandContext args) throws CommandException {
		if (!(src instanceof User)) {
			throw new CommandException(Text.of("You must be a player!"));
		}
		User from = (User) src;
		EconomyService service = game.getServiceManager().provide(EconomyService.class).get();
		User to = args.<User>getOne("player").get();
		BigDecimal amount = BigDecimal.valueOf(args.<Double>getOne("amount").get());
		Currency currency = args.<Currency>getOne("currency").orElseGet(service::getDefaultCurrency);
		Account fromAcc = service.getOrCreateAccount(from.getUniqueId()).get();
		Account toAcc = service.getOrCreateAccount(to.getUniqueId()).get();
		TransferResult result = fromAcc.transfer(toAcc, currency, amount, Cause.of(NamedCause.source(from), NamedCause.notifier(this)));
		switch (result.getResult()) {
		case SUCCESS:
			src.sendMessage(Text.of("Successfully paid ", to.getName(), " ", currency.format(amount), "."));
			if (to.isOnline())
				to.getPlayer().get().sendMessage(Text.of(from.getName()+" paid you "+currency.format(amount)+"."));
			return CommandResult.success();
		case ACCOUNT_NO_FUNDS:
			throw new CommandException(Text.of("You do not have enough ", currency.getPluralDisplayName(), " to complete the transaction."));
		case ACCOUNT_NO_SPACE:
			throw new CommandException(Text.of("They do not have enough space to store ", currency.format(amount), "."));
		case FAILED:
			throw new CommandException(Text.of("Invalid transaction."));
		default:
			throw new CommandException(Text.of("Unknown error."));
		}
	}
	void disable() {
		game.getEventManager().unregisterPluginListeners(this);
		game.getCommandManager().getOwnedBy(this).forEach(mapping -> game.getCommandManager().removeMapping(mapping));
	}
	PieconomyCurrency parse(Object obj, ConfigurationNode node, String defaultCurrency) {
		String id;
		if (obj instanceof String) id = (String) obj; else throw new IllegalArgumentException("Non-string key \""+obj+"\" found!");
		int decimal = node.getNode("decimal-places").getInt(2);
		Text name; try {name = node.getNode("name").getValue(TypeToken.of(Text.class), Text.of(id.substring(0, 1).toUpperCase()+id.substring(1)));} catch (ObjectMappingException e) {throw new IllegalArgumentException(Joiner.on(".").join(node.getNode("name").getPath())+": Could not parse object!");}
		Text plural; try {plural = node.getNode("plural").getValue(TypeToken.of(Text.class), Text.builder().format(name.getFormat()).append(name).append(Text.of("s")).build());} catch (ObjectMappingException e) {throw new IllegalArgumentException(Joiner.on(".").join(node.getNode("plural").getPath())+": Could not parse object!");}
		Text symbol; try {symbol = node.getNode("symbol").getValue(TypeToken.of(Text.class));} catch (ObjectMappingException e) {throw new IllegalArgumentException(Joiner.on(".").join(node.getNode("symbol").getPath())+": Could not parse object!");}
		TextTemplate format; try {format = node.getNode("format").getValue(TypeToken.of(TextTemplate.class), TextTemplate.of(TextTemplate.arg("symbol").build(), TextTemplate.arg("amount").build()));} catch (ObjectMappingException e) {throw new IllegalArgumentException(Joiner.on(".").join(node.getNode("format").getPath())+": Could not parse object!");}
		boolean isDefault = id.equals(defaultCurrency);
		return new PieconomyCurrency(id, name, plural, symbol, format, decimal, isDefault);
	}
}