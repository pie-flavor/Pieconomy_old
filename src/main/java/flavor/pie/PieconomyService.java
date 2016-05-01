package flavor.pie;

import java.util.*;

import org.spongepowered.api.Game;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.context.ContextCalculator;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.user.UserStorageService;

public class PieconomyService implements EconomyService {
	PieconomyService(Currency defaultCurrency, Set<Currency> currencies, Game game, Pieconomy plugin) {
		this.defaultCurrency = defaultCurrency;
		this.currencies = currencies;
		calculators = new HashSet<ContextCalculator<Account>>();
		accounts = new HashMap<UUID, UniqueAccount>();
		this.game = game;
		this.plugin = plugin;
	}
	Currency defaultCurrency;
	Set<Currency> currencies;
	Set<ContextCalculator<Account>> calculators;
	HashMap<UUID, UniqueAccount> accounts;
	Game game;
	Pieconomy plugin;
	@Override
	public void registerContextCalculator(ContextCalculator<Account> calculator) {
		calculators.add(calculator);
	}

	@Override
	public Currency getDefaultCurrency() {
		return defaultCurrency;
	}

	@Override
	public Set<Currency> getCurrencies() {
		return currencies;
	}

	@Override
	public boolean hasAccount(UUID uuid) {
		UserStorageService service = game.getServiceManager().provide(UserStorageService.class).get();
		Optional<User> user_ = service.get(uuid);
		return user_.isPresent();
	}

	@Override
	public boolean hasAccount(String identifier) {
		try {
			return hasAccount(UUID.fromString(identifier));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	@Override
	public Optional<UniqueAccount> getOrCreateAccount(UUID uuid) {
		if (accounts.containsKey(uuid)) {
			return Optional.of(accounts.get(uuid));
		}
		UserStorageService service = game.getServiceManager().provide(UserStorageService.class).get();
		Optional<User> user_ = service.get(uuid);
		if (user_.isPresent()) {
			PieconomyAccount account = new PieconomyAccount(user_.get(), game, plugin, this);
			accounts.put(uuid, account);
			return Optional.of(account);
		} else return Optional.empty();
	}

	@Override
	public Optional<Account> getOrCreateAccount(String identifier) {
		try {
			return getOrCreateAccount(UUID.fromString(identifier)).map(acc -> acc);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

}
