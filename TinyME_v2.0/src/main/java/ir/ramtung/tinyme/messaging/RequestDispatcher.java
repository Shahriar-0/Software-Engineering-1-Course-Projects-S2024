package ir.ramtung.tinyme.messaging;

import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import java.util.logging.Logger;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class RequestDispatcher {

	private final Logger log = Logger.getLogger(this.getClass().getName());
	private final OrderHandler orderHandler;

	public RequestDispatcher(OrderHandler orderHandler) {
		this.orderHandler = orderHandler;
	}

	@JmsListener(
			destination = "${requestQueue}",
			selector = "_type='ir.ramtung.tinyme.messaging.request.EnterOrderRq'"
	)
	public void receiveEnterOrderRq(EnterOrderRq enterOrderRq) {
		log.info("Received message: " + enterOrderRq);
		orderHandler.handleRq(enterOrderRq);
	}

	@JmsListener(
		destination = "${requestQueue}",
		selector = "_type='ir.ramtung.tinyme.messaging.request.DeleteOrderRq'"
	)
	public void receiveDeleteOrderRq(DeleteOrderRq deleteOrderRq) {
		log.info("Received message: " + deleteOrderRq);
		orderHandler.handleRq(deleteOrderRq);
	}

	@JmsListener(
			destination = "${requestQueue}",
			selector = "_type='ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq'"
	)
	public void receiveChangeMatchingStateRq(ChangeMatchingStateRq changeMatchingStateRq) {
		log.info("Received message: " + changeMatchingStateRq);
		orderHandler.handleRq(changeMatchingStateRq);
	}
}
