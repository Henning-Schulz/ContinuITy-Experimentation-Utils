package org.continuity.experimentation.action.continuity;

import org.continuity.api.entities.config.Order;
import org.continuity.api.entities.links.LinkExchangeModel;
import org.continuity.api.entities.report.OrderResponse;
import org.continuity.experimentation.Context;
import org.continuity.experimentation.action.AbstractRestAction;
import org.continuity.experimentation.data.IDataHolder;
import org.continuity.experimentation.data.SimpleDataHolder;
import org.continuity.experimentation.data.StaticDataHolder;
import org.continuity.experimentation.exception.AbortException;
import org.continuity.experimentation.exception.AbortInnerException;

public class OrderSubmission extends AbstractRestAction {

	/**
	 * Order
	 */
	private IDataHolder<Order> order;

	/**
	 * Dataholder for {@link OrderResponse}
	 */
	private IDataHolder<OrderResponse> orderResponse;

	/**
	 * Custom source, which might be set during the execution
	 */
	private IDataHolder<LinkExchangeModel> source;

	/**
	 * Constructor
	 *
	 * @param host
	 *            the host of the ContinuITy orchestrator
	 * @param port
	 *            the port of the ContinuITy orchestrator
	 */
	public OrderSubmission(String host, String port, Order order, IDataHolder<OrderResponse> orderResponse) {
		this(host, port, StaticDataHolder.of(order), orderResponse);
	}

	public OrderSubmission(String host, String port, IDataHolder<Order> order, IDataHolder<OrderResponse> orderResponse) {
		super(host, port);
		this.order = order;
		this.orderResponse = orderResponse;
		this.source = new SimpleDataHolder<>("source", LinkExchangeModel.class);
	}

	/**
	 * Constructor
	 *
	 * @param host
	 *            the host of the ContinuITy orchestrator
	 * @param port
	 *            the port of the ContinuITy orchestrator
	 * @param model
	 * {@link IDataHolder} will be set
	 */
	public OrderSubmission(String host, String port, Order order, IDataHolder<OrderResponse> orderResponse, IDataHolder<LinkExchangeModel> source) {
		this(host, port, StaticDataHolder.of(order), orderResponse, source);
	}

	public OrderSubmission(String host, String port, IDataHolder<Order> order, IDataHolder<OrderResponse> orderResponse, IDataHolder<LinkExchangeModel> source) {
		this(host, port, order, orderResponse);
		this.source = source;
	}

	@Override
	public void execute(Context context) throws AbortInnerException, AbortException, Exception {
		// Overwrite source if source is provided
		if(source.isSet()) {
			order.get().setSource(source.get());
		}
		orderResponse.set(post("/order/submit", OrderResponse.class, order));
	}

}
