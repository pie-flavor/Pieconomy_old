package flavor.pie.pieconomy;

import java.math.BigDecimal;
import java.math.MathContext;

import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.TextTemplate;

import com.google.common.collect.ImmutableMap;

public class PieconomyCurrency implements Currency {
	String id;
	Text displayName;
	Text pluralName;
	Text symbol;
	TextTemplate format;
	int fractionDigits;
	boolean isDefault;
	PieconomyCurrency(String id, Text displayName, Text pluralName, Text symbol, TextTemplate format, int fractionDigits, boolean isDefault) {
		this.id = id;
		this.displayName = displayName;
		this.pluralName = pluralName;
		this.symbol = symbol;
		this.format = format;
		this.fractionDigits = fractionDigits;
		this.isDefault = isDefault;
	}
	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getName() {
		return displayName.toPlain();
	}

	@Override
	public Text getDisplayName() {
		return displayName;
	}

	@Override
	public Text getPluralDisplayName() {
		return pluralName;
	}

	@Override
	public Text getSymbol() {
		return symbol;
	}

	@Override
	public Text format(BigDecimal amount, int numFractionDigits) {
		return format.apply(ImmutableMap.of("amount", Text.of(amount.round(new MathContext(numFractionDigits)).toPlainString()), "symbol", symbol)).build();
	}

	@Override
	public int getDefaultFractionDigits() {
		return fractionDigits;
	}

	@Override
	public boolean isDefault() {
		return isDefault;
	}

}
