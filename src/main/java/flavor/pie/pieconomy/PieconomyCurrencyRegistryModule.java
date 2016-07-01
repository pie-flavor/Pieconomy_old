package flavor.pie.pieconomy;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import org.spongepowered.api.registry.AdditionalCatalogRegistryModule;
import org.spongepowered.api.registry.RegistrationPhase;
import org.spongepowered.api.registry.util.DelayedRegistration;
import org.spongepowered.api.service.economy.Currency;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public class PieconomyCurrencyRegistryModule implements AdditionalCatalogRegistryModule<Currency> {
    BiMap<String, Currency> currencies;
    Set<Currency> defaults;
    PieconomyCurrencyRegistryModule(Set<Currency> currencies) {
        this.currencies = HashBiMap.create();
        defaults = currencies;
    }
    @Override
    public void registerAdditionalCatalog(Currency extraCatalog) {
        currencies.put(extraCatalog.getId(), extraCatalog);
    }

    @Override
    public Optional<Currency> getById(String id) {
        return Optional.ofNullable(currencies.get(id));
    }

    @Override
    public Collection<Currency> getAll() {
        return ImmutableSet.copyOf(currencies.values());
    }

    @Override
    @DelayedRegistration(RegistrationPhase.INIT)
    public void registerDefaults() {
        for (Currency currency : defaults) {
            currencies.put(currency.getId(), currency);
        }
    }
}
