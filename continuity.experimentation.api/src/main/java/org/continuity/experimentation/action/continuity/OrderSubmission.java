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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;

public class OrderSubmission extends AbstractRestAction {

	private static final Logger LOGGER = LoggerFactory.getLogger(OrderSubmission.class);

	private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory().enable(Feature.MINIMIZE_QUOTES).enable(Feature.USE_NATIVE_OBJECT_ID));

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
			LOGGER.info("Overwrote source of order '{}' with '{}'.", order, source);
		}
		orderResponse.set(post("/order/submit", OrderResponse.class, order.get()));

		ObjectWriter writer = MAPPER.writer(new DefaultPrettyPrinter());
		writer.writeValue(context.toPath().resolve("order.yml").toFile(), order.get());
		writer.writeValue(context.toPath().resolve("order-response.yml").toFile(), orderResponse.get());

		LOGGER.info("Submitted an order and stored it to {}", context.toPath().resolve("order.yml"));

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Order response is {}", writer.writeValueAsString(orderResponse.get()));
		}
	}

}
