package ir.ramtung.tinyme.domain.service.security_state;

import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.OrderBook;
import ir.ramtung.tinyme.domain.entity.SecurityState;
import ir.ramtung.tinyme.domain.entity.StopLimitOrder;
import ir.ramtung.tinyme.domain.entity.Trade;
import ir.ramtung.tinyme.domain.entity.stats.AuctionStats;
import ir.ramtung.tinyme.domain.entity.stats.ExecuteStats;
import ir.ramtung.tinyme.domain.entity.stats.SecurityStats;
import ir.ramtung.tinyme.domain.entity.stats.SituationalStats;
import ir.ramtung.tinyme.domain.entity.stats.StateStats;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.controls.ControlResult;
import ir.ramtung.tinyme.domain.service.controls.CreditControl;
import ir.ramtung.tinyme.domain.service.controls.PositionControl;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuctionBehave implements SecurityBehave {

	private PositionControl positionControl;
	private CreditControl creditControl;
	private Matcher matcher;

	public AuctionBehave(PositionControl positionControl, CreditControl creditControl, Matcher matcher) {
		this.positionControl = positionControl;
		this.creditControl = creditControl;
		this.matcher = matcher;
	}

	@Override
	public List<SecurityStats> addNewOrder(Order newOrder, OrderBook orderBook, int lastTradePrice) {
		if (positionControl.checkPositionForOrder(newOrder, orderBook) != ControlResult.OK) {
			return createNotEnoughPositionsStats(newOrder);
		}
		if (creditControl.checkCreditForBeingQueued(newOrder) != ControlResult.OK) {
			return createNotEnoughCreditStats(newOrder);
		}

		creditControl.updateCreditForBeingQueued(newOrder);
		orderBook.enqueue(newOrder);

		List<SecurityStats> stats = new ArrayList<>();
		stats.add(SituationalStats.createAddOrderStats(newOrder.getOrderId()));
		stats.add(createAuctionStats(orderBook, lastTradePrice));
		return stats;
	}

	@Override
	public List<SecurityStats> updateOrder(Order tempOrder, Order mainOrder, OrderBook orderBook, int lastTradePrice) {
		boolean losesPriority = mainOrder.willPriorityLostInUpdate(tempOrder);
		if (losesPriority) {
			Order originalOrder = mainOrder.snapshot();
			creditControl.updateCreditAtDelete(mainOrder);
			orderBook.removeOrder(mainOrder);
			mainOrder.updateFromTempOrder(tempOrder);
			return reAddUpdatedOrder(mainOrder, originalOrder, orderBook, lastTradePrice);
		} else {
			return updateByKeepingPriority(tempOrder, mainOrder, orderBook, lastTradePrice);
		}
	}

	@Override
	public List<SecurityStats> deleteOrder(Order targetOrder, OrderBook orderBook, int lastTradePrice) {
		creditControl.updateCreditAtDelete(targetOrder);
		orderBook.removeOrder(targetOrder);

		List<SecurityStats> stats = new ArrayList<>();
		stats.add(SituationalStats.createDeleteOrderStats(targetOrder.getOrderId()));
		stats.add(createAuctionStats(orderBook, lastTradePrice));
		return stats;
	}

	@Override
	public List<SecurityStats> activateStopLimitOrders(OrderBook orderBook, int lastTradePrice) {
		List<SecurityStats> stats = new LinkedList<>();
		StopLimitOrder slo;

		while ((slo = orderBook.getStopLimitOrder(lastTradePrice)) != null) {
			stats.add(SituationalStats.createOrderActivatedStats(slo.getOrderId(), slo.getRequestId()));
			Order activatedOrder = new Order(slo);
			orderBook.enqueue(activatedOrder);
		}

		return stats;
	}

	@Override
	public List<SecurityStats> changeMatchingState(OrderBook orderBook, int lastTradePrice, SecurityState newState) {
		List<SecurityStats> stats = openAuction(orderBook, lastTradePrice);
		stats.add(StateStats.createStateStats(SecurityState.AUCTION, newState));
		return stats;
	}

	private AuctionStats createAuctionStats(OrderBook orderBook, int lastTradePrice) {
		int openingPrice = matcher.calcOpeningAuctionPrice(orderBook, lastTradePrice);
		int tradableQuantity = matcher.calcTradableQuantity(orderBook, openingPrice);
		return AuctionStats.createAuctionStats(openingPrice, tradableQuantity);
	}

	private List<SecurityStats> openAuction(OrderBook orderBook, int lastTradePrice) {
		List<SecurityStats> stats = new ArrayList<>();

		List<Trade> trades = matcher.auctionExecuting(orderBook, lastTradePrice).trades();
		if (!trades.isEmpty()) {
			stats.add(ExecuteStats.createAuctionExecuteStats(trades));
		}

		return stats;
	}

	private List<SecurityStats> reAddUpdatedOrder(Order updatedOrder, Order originalOrder, 
												  OrderBook orderBook, int lastTradePrice) {
													
		if (positionControl.checkPositionForOrder(updatedOrder, orderBook) != ControlResult.OK) {
			return handleNotEnoughPositions(originalOrder, orderBook);
		}
		
		if (creditControl.checkCreditForBeingQueued(updatedOrder) != ControlResult.OK) {
			return handleNotEnoughCredit(originalOrder, orderBook);
		}

		return handleUpdateOrder(updatedOrder, originalOrder, orderBook, lastTradePrice);
	}

	private List<SecurityStats> handleUpdateOrder(Order updatedOrder, Order originalOrder, OrderBook orderBook,
			int lastTradePrice) {
		creditControl.updateCreditForBeingQueued(updatedOrder);
		orderBook.enqueue(updatedOrder);

		return createUpdateStats(originalOrder, orderBook, lastTradePrice);
	}

	private List<SecurityStats> createUpdateStats(Order originalOrder, OrderBook orderBook, int lastTradePrice) {
		List<SecurityStats> stats = new LinkedList<>();
		stats.add(SituationalStats.createUpdateOrderStats(originalOrder.getOrderId()));
		stats.add(createAuctionStats(orderBook, lastTradePrice));
		return stats;
	}

	private List<SecurityStats> handleNotEnoughCredit(Order originalOrder, OrderBook orderBook) {
		creditControl.updateCreditForBeingQueued(originalOrder);
		orderBook.enqueue(originalOrder);
		return createNotEnoughCreditStats(originalOrder);
	}

	private List<SecurityStats> createNotEnoughCreditStats(Order originalOrder) {
		return new ArrayList<SecurityStats>(
			List.of(SituationalStats.createNotEnoughCreditStats(originalOrder.getOrderId()))
		);
	}

	private List<SecurityStats> handleNotEnoughPositions(Order originalOrder, OrderBook orderBook) {
		creditControl.updateCreditForBeingQueued(originalOrder);
		orderBook.enqueue(originalOrder);
		return createNotEnoughPositionsStats(originalOrder);
	}

	private List<SecurityStats> createNotEnoughPositionsStats(Order originalOrder) {
		return new ArrayList<SecurityStats>(
			List.of(SituationalStats.createNotEnoughPositionsStats(originalOrder.getOrderId()))
		);
	}

	private List<SecurityStats> updateByKeepingPriority(Order tempOrder, Order mainOrder, 
														OrderBook orderBook, int lastTradePrice) {

		mainOrder.updateFromTempOrder(tempOrder);
		return createUpdateStats(mainOrder, orderBook, lastTradePrice);
	}
}
