package flavor.pie;

import java.math.BigDecimal;
import java.util.Set;

import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.transaction.*;

public class PieconomyTransferResult extends PieconomyTransactionResult implements TransferResult {
	PieconomyTransferResult(Account account, Account accountTo, Currency currency, BigDecimal amount, Set<Context> contexts,
			ResultType resultType, TransactionType transactionType) {
		super(account, currency, amount, contexts, resultType, transactionType);
		this.accountTo = accountTo;
	}
	Account accountTo;
	@Override
	public Account getAccountTo() {
		return accountTo;
	}
	
}
