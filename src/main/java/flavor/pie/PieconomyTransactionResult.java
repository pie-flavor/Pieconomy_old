package flavor.pie;

import java.math.BigDecimal;
import java.util.Set;

import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.transaction.*;

public class PieconomyTransactionResult implements TransactionResult {
	Account account;
	Currency currency;
	BigDecimal amount;
	Set<Context> contexts;
	ResultType resultType;
	TransactionType transactionType;
	PieconomyTransactionResult(Account account, Currency currency, BigDecimal amount, Set<Context> contexts, ResultType resultType, TransactionType transactionType) {
		this.account = account;
		this.currency = currency;
		this.amount = amount;
		this.contexts = contexts;
		this.resultType = resultType;
		this.transactionType = transactionType;
	}
	@Override
	public Account getAccount() {
		return account;
	}

	@Override
	public Currency getCurrency() {
		return currency;
	}

	@Override
	public BigDecimal getAmount() {
		return amount;
	}

	@Override
	public Set<Context> getContexts() {
		return contexts;
	}

	@Override
	public ResultType getResult() {
		return resultType;
	}

	@Override
	public TransactionType getType() {
		return transactionType;
	}

}
