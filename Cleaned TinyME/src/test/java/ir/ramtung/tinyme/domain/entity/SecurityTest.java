package ir.ramtung.tinyme.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ir.ramtung.tinyme.domain.exception.NotFoundException;
import ir.ramtung.tinyme.domain.service.Matcher;

@SpringBootTest
public class SecurityTest {
    private Security security;
    private Broker sellerBroker;
    private Broker buyerBroker;
    private Shareholder sellerShareholder;
    private Shareholder buyerShareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private Matcher matcher;

    private static class AssertingPack {
        private static Security security;
        private static Broker sellerBroker;
        private static Broker buyerBroker;
        private static Shareholder sellerShareholder;
        private static Shareholder buyerShareholder;
        private static long exceptedSellerCredit;
        private static long exceptedBuyerCredit;
        private static Integer exceptedSellerPosition;
        private static Integer exceptedBuyerPosition;
        private static LinkedList<Order> sellQueue;
        private static LinkedList<Order> buyQueue;

        private static void initialize() {
            exceptedSellerCredit = sellerBroker.getCredit();
            exceptedBuyerCredit = buyerBroker.getCredit();
            exceptedSellerPosition = sellerShareholder.getPositionBySecurity(security);
            exceptedBuyerPosition = buyerShareholder.getPositionBySecurity(security);
        }

        private static void assertSellerCredit() {
            assertThat(sellerBroker.getCredit()).isEqualTo(exceptedSellerCredit);
        }

        private static void assertBuyerCredit() {
            assertThat(buyerBroker.getCredit()).isEqualTo(exceptedBuyerCredit);
        }

        private static void assertSellerPosition() {
            assertThat(sellerShareholder.getPositionBySecurity(security)).isEqualTo(exceptedSellerPosition);
        }

        private static void assertBuyerPosition() {
            assertThat(buyerShareholder.getPositionBySecurity(security)).isEqualTo(exceptedBuyerPosition);
        }

        private static void assertCredits() {
            assertSellerCredit();
            assertBuyerCredit();
        }

        private static void assertPositions() {
            assertSellerPosition();
            assertBuyerPosition();
        }

        private static void assertAll() {
            assertCredits();
            assertPositions();
        }

        private static void assertOrderInQueue(Side side, int idx, long orderId, int quantity, int price) {
            Order order = (side == Side.BUY) ? buyQueue.get(idx) : sellQueue.get(idx);
            long actualId = order.getOrderId();
            int actualquantity = order.getTotalQuantity();
            int actualPrice = order.getPrice();

            assertThat(actualId).isEqualTo(orderId);
            assertThat(actualquantity).isEqualTo(quantity);
            assertThat(actualPrice).isEqualTo(price);
        }

        private static void assertOrderInQueue(Side side, int idx, long orderId, int quantity, int price, int peakSize, int displayedQuantity) {
            assertOrderInQueue(side, idx, orderId, quantity, price);
            Order order = (side == Side.BUY) ? buyQueue.get(idx) : sellQueue.get(idx);
            IcebergOrder iceOrder = (IcebergOrder) order;
            int actualPeakSize = iceOrder.getPeakSize(); 
            int actualDisplayedQuantity = iceOrder.getDisplayedQuantity();

            assertThat(actualPeakSize).isEqualTo(peakSize);
            assertThat(actualDisplayedQuantity).isEqualTo(displayedQuantity);
        }
    }

    @BeforeEach
    void setup() {
        security = Security.builder().build();
        sellerBroker = Broker.builder().credit(0).build();
        buyerBroker = Broker.builder().credit(0).build();
        sellerShareholder = Shareholder.builder().build();
        buyerShareholder = Shareholder.builder().build();
        sellerShareholder.incPosition(security, 85);
        buyerShareholder.incPosition(security, 0);
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
            new Order(1, security, Side.BUY, 10, 100, buyerBroker, buyerShareholder),
            new Order(2, security, Side.BUY, 10, 200, buyerBroker, buyerShareholder),
            new Order(3, security, Side.BUY, 10, 300, buyerBroker, buyerShareholder),
            new Order(4, security, Side.BUY, 10, 400, buyerBroker, buyerShareholder),
            new IcebergOrder(5, security, Side.BUY, 45, 500, buyerBroker, buyerShareholder, 10),
            new Order(1, security, Side.SELL, 10, 600, sellerBroker, sellerShareholder),
            new Order(2, security, Side.SELL, 10, 700, sellerBroker, sellerShareholder),
            new Order(3, security, Side.SELL, 10, 800, sellerBroker, sellerShareholder),
            new Order(4, security, Side.SELL, 10, 900, sellerBroker, sellerShareholder),
            new IcebergOrder(5, security, Side.SELL, 45, 1000, sellerBroker, sellerShareholder, 10)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        AssertingPack.security = this.security;
        AssertingPack.sellerBroker = this.sellerBroker;
        AssertingPack.buyerBroker = this.buyerBroker;
        AssertingPack.sellerShareholder = this.sellerShareholder;
        AssertingPack.buyerShareholder = this.buyerShareholder;
        AssertingPack.sellQueue = orderBook.getSellQueue();
        AssertingPack.buyQueue = orderBook.getBuyQueue();
        AssertingPack.initialize();
    }

    @Test
    public void delete_sell_order() {
        security.deleteOrder(Side.SELL, 2);
        
        AssertingPack.assertAll();
        AssertingPack.assertOrderInQueue(Side.SELL, 1, 3, 10, 800);
        AssertingPack.assertOrderInQueue(Side.BUY, 3, 2, 10, 200);
    }

    @Test
    public void delete_buy_order() {
        security.deleteOrder(Side.BUY, 3);
        
        AssertingPack.exceptedBuyerCredit = 3000;
        AssertingPack.assertAll();
        AssertingPack.assertOrderInQueue(Side.SELL, 1, 2, 10, 700);
        AssertingPack.assertOrderInQueue(Side.BUY, 2, 2, 10, 200);
    }

    @Test
    public void delete_sell_ice_order() {
        security.deleteOrder(Side.SELL, 5);

        AssertingPack.assertAll();
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> orderBook.getSellQueue().get(4));
        AssertingPack.assertOrderInQueue(Side.BUY, 0, 5, 45, 500, 10, 10);
    }

    @Test
    public void delete_buy_ice_order() {
        security.deleteOrder(Side.BUY, 5);

        AssertingPack.exceptedBuyerCredit = 22500;
        AssertingPack.assertAll();
        AssertingPack.assertOrderInQueue(Side.SELL, 4, 5, 45, 1000, 10, 10);
        AssertingPack.assertOrderInQueue(Side.BUY, 0, 4, 10, 400);
    }

    @Test
    public void delete_non_existing_order() {
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> security.deleteOrder(Side.SELL, 6));
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> security.deleteOrder(Side.BUY, 8));
        AssertingPack.assertAll();
    }

    @Test
    public void decrease_sell_order_quantity() {
        Order updatedOrder = new Order(1, security, Side.SELL, 4, 600, sellerBroker, sellerShareholder);
        security.updateOrder(updatedOrder, matcher);

        AssertingPack.assertAll();
        AssertingPack.assertOrderInQueue(Side.SELL, 0, 1, 4, 600);
        // TODO
        // what if new quantity be zero? what should happend in that case?
    }

    @Test
    public void decrease_buy_order_quantity() {
        Order updatedOrder = new Order(3, security, Side.BUY, 7, 300, buyerBroker, buyerShareholder);
        security.updateOrder(updatedOrder, matcher);

        AssertingPack.exceptedBuyerCredit = 900;
        AssertingPack.assertAll();
        AssertingPack.assertOrderInQueue(Side.BUY, 2, 3, 7, 300);
    }

    @Test 
    public void decrease_sell_ice_order_quantity() {
        IcebergOrder updatedOrder = new IcebergOrder(5, security, Side.SELL, 30, 1000, sellerBroker, sellerShareholder, 10);
        security.updateOrder(updatedOrder, matcher);

        AssertingPack.assertAll();
        AssertingPack.assertOrderInQueue(Side.SELL, 4, 5, 30, 1000, 10, 10);
    }

    @Test 
    public void decrease_buy_ice_order_quantity() {
        IcebergOrder updatedOrder = new IcebergOrder(5, security, Side.BUY, 7, 500, sellerBroker, sellerShareholder, 10);
        security.updateOrder(updatedOrder, matcher);

        AssertingPack.exceptedBuyerCredit = 19000;
        AssertingPack.assertAll();
        AssertingPack.assertOrderInQueue(Side.BUY, 0, 5, 7, 500, 10, 7);
    }

    // TODO
    // add peakSize scenarios after you are sure how they work

    @Test
    public void increase_sell_order_quantity() {
        Order updatedOrder = new Order(2, security, Side.SELL, 15, 700, sellerBroker, sellerShareholder);
        sellerShareholder.incPosition(security, 5);
        security.updateOrder(updatedOrder, matcher);

        AssertingPack.exceptedSellerPosition = 90;
        AssertingPack.assertAll();
        AssertingPack.assertOrderInQueue(Side.SELL, 1, 2, 15, 700);
    }

    @Test
    public void increase_sell_order_quantity_but_hasnt_enough_position() {
        Order updatedOrder = new Order(2, security, Side.SELL, 15, 700, sellerBroker, sellerShareholder);
        MatchingOutcome res = security.updateOrder(updatedOrder, matcher).outcome();

        AssertingPack.assertAll();
        assertThat(res).isEqualTo(MatchingOutcome.NOT_ENOUGH_POSITIONS);
    }
}
