package flavor.pie.pieconomy;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.api.Game;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult;
import org.spongepowered.api.item.inventory.type.CarriedInventory;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.service.economy.transaction.TransactionTypes;
import org.spongepowered.api.service.economy.transaction.TransferResult;
import org.spongepowered.api.text.Text;

import java.math.BigDecimal;
import java.util.*;

public class PieconomyAccount implements UniqueAccount {
    User user;
    Game game;
    Pieconomy plugin;
    PieconomyService service;
    PieconomyAccount(User user, Game game, Pieconomy plugin, PieconomyService service) {
        this.user = user.getPlayer().orElseThrow(RuntimeException::new); //TODO Inventory API :(
        this.game = game;
        this.plugin = plugin;
        this.service = service;
    }
    @Override
    public Text getDisplayName() {
        return user.get(Keys.DISPLAY_NAME).get();
    }

    @Override
    public BigDecimal getDefaultBalance(Currency currency) {
        return new BigDecimal(0);
    }

    @Override
    public boolean hasBalance(Currency currency, Set<Context> contexts) {
        if (!user.isOnline()) {
            return false; //TODO Inventory API :(
        }
        CarriedInventory<User> inv = (CarriedInventory<User>) user.getInventory();
        for (Map.Entry<ItemType, Pair<BigDecimal, Currency>> entry : plugin.items.entrySet()) {
            if (entry.getValue().getValue().equals(currency) && entry.getValue().getKey().compareTo(BigDecimal.ZERO) > 0 && inv.contains(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BigDecimal getBalance(Currency currency, Set<Context> contexts) {
        if (!user.isOnline()) {
            return BigDecimal.ZERO; // TODO Inventory API :(
        }
        CarriedInventory<User> inv = (CarriedInventory<User>) user.getInventory();
        BigDecimal bal = BigDecimal.ZERO;
        for (Map.Entry<ItemType, Pair<BigDecimal, Currency>> entry : plugin.items.entrySet()) {
            if (entry.getValue().getValue().equals(currency)) {
                for (Slot slot : inv.<Slot>slots()) {
                    if (!slot.isEmpty() && slot.peek().get().getItem().equals(entry.getKey()))
                        bal = bal.add(entry.getValue().getKey().multiply(new BigDecimal(slot.getStackSize())));
                }
            }
        }
        return bal;
    }

    @Override
    public Map<Currency, BigDecimal> getBalances(Set<Context> contexts) {
        Map<Currency, BigDecimal> map = Maps.newHashMap();
        if (!user.isOnline()) {
            return map; //TODO Inventory API :(
        }
        for (Currency currency : service.getCurrencies()) {
            map.put(currency, getBalance(currency, contexts));
        }
        return map;
    }

    @Override
    public TransactionResult setBalance(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        if (!user.isOnline()) {
            return new PieconomyTransactionResult(this, currency, amount, contexts, ResultType.FAILED, TransactionTypes.DEPOSIT); //TODO Inventory API :(
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new PieconomyTransactionResult(this, currency, amount, contexts, ResultType.FAILED, TransactionTypes.DEPOSIT);
        }
        BigDecimal toAdd = amount;
        Map<ItemType, BigDecimal> map = Maps.transformValues(Maps.filterValues(plugin.items, v -> v.getValue().equals(currency)), Pair::getKey);
        List<Map.Entry<ItemType, BigDecimal>> list = new ArrayList<>();
        list.addAll(map.entrySet());
        Collections.sort(list, (t1, t2) -> t1.getValue().compareTo(t2.getValue()));
        Inventory inv = user.getInventory();
        ArrayList<ItemStack> stacksRemoved = new ArrayList<>();
        ArrayList<ItemStack> stacksAdded = new ArrayList<>();
        Inventory query = inv.query(map.keySet());
        for (Slot s : inv.<Slot>slots()) {
            if (s.isEmpty() || !map.keySet().contains(s.peek().get().getItem())) continue;
            Optional<ItemStack> item_ = s.poll();
            if (item_.isPresent()) {
                stacksRemoved.add(item_.get());
            }
        }
        boolean failed = false;
        if (amount.compareTo(BigDecimal.TEN.pow(-currency.getDefaultFractionDigits())) < 0)
            add: for (Map.Entry<ItemType, BigDecimal> entry : list) {
                while (entry.getValue().compareTo(amount) <= 0) {
                    ItemStack stack = ItemStack.builder().itemType(entry.getKey()).build();
                    ItemStackSnapshot snapshot = stack.createSnapshot();
                    InventoryTransactionResult result = inv.offer(stack);
                    if (result.getType().equals(InventoryTransactionResult.Type.SUCCESS)) {
                        toAdd = toAdd.subtract(entry.getValue());
                        stacksAdded.add(ItemStack.builder().fromSnapshot(snapshot).quantity(-1).build());
                        failed = false;
                    } else {
                        failed = true;
                        break;
                    }
                    if (toAdd.compareTo(BigDecimal.TEN.pow(-currency.getDefaultFractionDigits())) < 0) {
                        break add;
                    }
                }
            }

        if (failed) {
            stacksAdded.forEach(s -> inv.query(s).poll(1));
            stacksRemoved.forEach(inv::offer);
            return new PieconomyTransactionResult(this, currency, amount, contexts, ResultType.ACCOUNT_NO_SPACE, TransactionTypes.DEPOSIT);
        } else
            return new PieconomyTransactionResult(this, currency, amount, contexts, ResultType.SUCCESS, TransactionTypes.DEPOSIT);
    }

    @Override
    public Map<Currency, TransactionResult> resetBalances(Cause cause, Set<Context> contexts) {
        Map<Currency, TransactionResult> map = new HashMap<>();
        for (Currency currency : service.getCurrencies()) {
            map.put(currency, resetBalance(currency, cause, contexts));
        }
        return map;
    }

    @Override
    public TransactionResult resetBalance(Currency currency, Cause cause, Set<Context> contexts) {
        return setBalance(currency, BigDecimal.ZERO, cause, contexts);
    }

    @Override
    public TransactionResult deposit(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        if (!user.isOnline()) {
            return new PieconomyTransactionResult(this, currency, amount, contexts, ResultType.FAILED, TransactionTypes.DEPOSIT); //TODO Inventory API :(
        }
        ArrayList<ItemStack> itemsAdded = new ArrayList<>();
        BigDecimal toAdd = amount;
        Map<ItemType, BigDecimal> map = Maps.transformValues(Maps.filterValues(plugin.items, v -> v.getValue().equals(currency)), Pair::getKey);
        List<Map.Entry<ItemType, BigDecimal>> list = new ArrayList<>();
        list.addAll(map.entrySet());
        Collections.sort(list, (t1, t2) -> t1.getValue().compareTo(t2.getValue()));
        Inventory inv = user.getInventory();
        boolean failed = false;
        add: for (Map.Entry<ItemType, BigDecimal> entry : list) {
            while (amount.compareTo(entry.getValue()) >= 0) {
                ItemStack stack = ItemStack.builder().itemType(entry.getKey()).build();
                ItemStackSnapshot snapshot = stack.createSnapshot();
                InventoryTransactionResult result = inv.offer(stack);
                if (result.getType().equals(InventoryTransactionResult.Type.SUCCESS)) {
                    failed = false;
                    itemsAdded.add(ItemStack.builder().fromSnapshot(snapshot).quantity(-1).build());
                    toAdd = toAdd.subtract(entry.getValue());
                } else {
                    failed = true;
                    break;
                }
                if (toAdd.compareTo(BigDecimal.TEN.pow(-currency.getDefaultFractionDigits())) < 0) {
                    break add;
                }
            }
        }
        if (failed) {
            itemsAdded.forEach(s -> inv.query(s).poll(1));
            return new PieconomyTransactionResult(this, currency, amount, contexts, ResultType.ACCOUNT_NO_SPACE, TransactionTypes.DEPOSIT);
        } else {
            return new PieconomyTransactionResult(this, currency, amount, contexts, ResultType.SUCCESS, TransactionTypes.DEPOSIT);
        }
    }

    @Override
    public TransactionResult withdraw(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        if (!user.isOnline()) {
            return new PieconomyTransactionResult(this, currency, amount, contexts, ResultType.FAILED, TransactionTypes.WITHDRAW); // TODO Inventory API :(
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new PieconomyTransactionResult(this, currency, amount, contexts, ResultType.FAILED, TransactionTypes.WITHDRAW);
        }
        ArrayList<ItemStack> itemsRemoved = new ArrayList<>();
        BigDecimal toRemove = amount;
        Map<ItemType, BigDecimal> map = Maps.transformValues(Maps.filterValues(plugin.items, v -> v.getValue().equals(currency)), Pair::getKey);
        List<Map.Entry<ItemType, BigDecimal>> list = new ArrayList<>();
        list.addAll(map.entrySet());
        Collections.sort(list, (t1, t2) -> t1.getValue().compareTo(t2.getValue()));
        Inventory inv = user.getInventory();
        boolean failed = false;
        remove: for (Map.Entry<ItemType, BigDecimal> entry : list) {
            while (amount.compareTo(entry.getValue()) >= 0) {
                Optional<ItemStack> stack_ = inv.query(entry.getKey()).poll(1);
                if (stack_.isPresent()) {
                    ItemStack stack = stack_.get();
                    itemsRemoved.add(stack);
                    toRemove = toRemove.subtract(entry.getValue());
                    failed = false;
                } else {
                    failed = true;
                    break;
                }
                if (toRemove.compareTo(BigDecimal.TEN.pow(-currency.getDefaultFractionDigits())) < 0) {
                    break remove;
                }
            }
        }
        if (failed) {
            itemsRemoved.forEach(inv::offer);
            return new PieconomyTransactionResult(this, currency, amount, contexts, ResultType.ACCOUNT_NO_FUNDS, TransactionTypes.WITHDRAW);
        } else {
            return new PieconomyTransactionResult(this, currency, amount, contexts, ResultType.SUCCESS, TransactionTypes.WITHDRAW);
        }
    }

    @Override
    public TransferResult transfer(Account to, Currency currency, BigDecimal amount, Cause cause,
            Set<Context> contexts) {
        TransactionResult withdraw = withdraw(currency, amount, cause, contexts);
        if (withdraw.getResult().equals(ResultType.ACCOUNT_NO_FUNDS)) {
            return new PieconomyTransferResult(this, to, currency, amount, contexts, ResultType.ACCOUNT_NO_FUNDS, TransactionTypes.TRANSFER);
        }
        if (withdraw.getResult().equals(ResultType.FAILED)){
            return new PieconomyTransferResult(this, to, currency, amount, contexts, ResultType.FAILED, TransactionTypes.TRANSFER);
        }
        TransactionResult deposit = deposit(currency, amount, cause, contexts);
        if (deposit.getResult().equals(ResultType.ACCOUNT_NO_SPACE)) {
            return new PieconomyTransferResult(this, to, currency, amount, contexts, ResultType.ACCOUNT_NO_SPACE, TransactionTypes.TRANSFER);
        }
        if (deposit.getResult().equals(ResultType.FAILED)){
            return new PieconomyTransferResult(this, to, currency, amount, contexts, ResultType.FAILED, TransactionTypes.TRANSFER);
        }
        return new PieconomyTransferResult(this, to, currency, amount, contexts, ResultType.SUCCESS, TransactionTypes.TRANSFER);
    }

    @Override
    public String getIdentifier() {
        return "PieconomyAccount:"+user.getUniqueId();
    }

    @Override
    public Set<Context> getActiveContexts() {
        return ImmutableSet.of();
    }

    @Override
    public UUID getUniqueId() {
        return user.getUniqueId();
    }

}
