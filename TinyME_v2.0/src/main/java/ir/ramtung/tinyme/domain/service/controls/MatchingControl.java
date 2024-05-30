package ir.ramtung.tinyme.domain.service.controls;

import java.util.List;

import org.springframework.stereotype.Service;

import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.OrderBook;
import ir.ramtung.tinyme.domain.entity.Trade;

@Service
public class MatchingControl {
    private PositionControl positionControl;
    private CreditControl creditControl;
    private QuantityControl quantityControl;

    public MatchingControl(PositionControl positionControl, CreditControl creditControl, QuantityControl quantityControl) {
        this.positionControl = positionControl;
        this.creditControl = creditControl;
        this.quantityControl = quantityControl;
    }

    public ControlResult checkBeforeContinuousMatching(Order targetOrder, OrderBook orderBook) {
        return positionControl.checkPositionForOrder(targetOrder, orderBook);
    }

    public void actionAtBeforeContinuousMatching(Order targetOrder, OrderBook orderBook) {

    }

    public void failedAtBeforContinuousMatching(Order targetOrder, OrderBook orderBook) {
        
    }

    public ControlResult checkBeforeMatchInContinuousMatching(Order targetOrder, Order matchingOrder) {
        return creditControl.chekCreditForContinousMatching(targetOrder, matchingOrder);
    }

    public void actionAtMatchingInContinuousMatching(Trade trade, OrderBook orderBook) {
        creditControl.updateCreditsAtTrade(trade);
        quantityControl.updateQuantitiesAtTrade(trade);
        positionControl.updatePositionsAtTrade(trade);
    }

    public void failedAtBeforeMatchInContinuousMatching(List<Trade> trades, OrderBook orderBook) {
        rollbackTrades(trades, orderBook);
    }

    public ControlResult checkAfterContinuousMatching(Order targetOrder, List<Trade> trades) {
        ControlResult controlResult = quantityControl.checkMinimumExecutionQuantity(targetOrder, trades);
        if (controlResult != ControlResult.OK) {
            return controlResult;
        }

        return creditControl.checkCreditForBeQueued(targetOrder);
    }

    public void actionAtAfterContinuousMatching(Order targetOrder, OrderBook orderBook) {
        creditControl.updateCreditAfterContinuousMatching(targetOrder);
        quantityControl.updateQuantityAfterContinuousMatching(targetOrder, orderBook);
    }

    public void failedAtAfterContinuousMatching(List<Trade> trades, OrderBook orerrBook) {
        rollbackTrades(trades, orerrBook);
    }

    private void rollbackTrades(List<Trade> trades, OrderBook orderBook) {
        for (Trade trade : trades.reversed()) {
            creditControl.updateCreditsAtRollbackTrade(trade);
            quantityControl.updateQuantitiesAtRollbackTrade(trade);
            positionControl.updatePositionsAtRollbackTrade(trade);
        }
    }
}
