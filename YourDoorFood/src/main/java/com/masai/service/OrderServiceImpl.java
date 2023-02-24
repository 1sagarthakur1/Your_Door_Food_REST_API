package com.masai.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import com.masai.exception.CustomerException;
import com.masai.exception.FoodCartException;
import com.masai.exception.ItemException;
import com.masai.exception.LoginException;
import com.masai.exception.OrderDetailsException;
import com.masai.exception.RestaurantException;
import com.masai.model.CurrentUserSession;
import com.masai.model.Customer;
import com.masai.model.FoodCart;
import com.masai.model.Item;
import com.masai.model.ItemRestaurantDTO;
import com.masai.model.OrderDetails;
import com.masai.model.Restaurant;
import com.masai.model.Status;
import com.masai.repository.CustomerRepo;
import com.masai.repository.ItemRepo;
import com.masai.repository.OrderDetailsRepo;
import com.masai.repository.RestaurantRepo;
import com.masai.repository.SessionRepo;

import ch.qos.logback.core.joran.conditional.IfAction;
import net.bytebuddy.build.HashCodeAndEqualsPlugin.WithNonNullableFields;

public class OrderServiceImpl implements OrderService{
	@Autowired
	private OrderDetailsRepo orderDetailsRepo;
	
	@Autowired
	private SessionRepo sessionRepo;
	
	@Autowired
	private CustomerRepo customerRepo;
	
	@Autowired
	private ItemRepo itemRepo;
	
	@Autowired
	private RestaurantRepo restaurantRepo;
	
	@Override
	public OrderDetails addOrder(String key, String paymentType) throws OrderDetailsException, LoginException, CustomerException, FoodCartException, ItemException {
		
		CurrentUserSession currentUserSession = sessionRepo.findByUuid(key);
		
		if(currentUserSession==null) throw new LoginException("Please login to place your order");
		Customer customer= customerRepo.findById(currentUserSession.getId()).get();
		FoodCart foodCart= customer.getFoodCart();
		Map<ItemRestaurantDTO, Integer> dtoMap= foodCart.getItemsDTO();
		if(dtoMap.isEmpty()) throw new FoodCartException("Cart is empty");
		
		for(Map.Entry<ItemRestaurantDTO, Integer> entry: dtoMap.entrySet()) {
			if(entry.getKey().getItem().getQuantity()<entry.getValue()) {
				throw new ItemException("Insufficient item quantity to the restaurant");				
			}
		}
		Double sum=0.0;
		for(Map.Entry<ItemRestaurantDTO, Integer> entry: dtoMap.entrySet()) {
			
			Item item= entry.getKey().getItem();
			item.setQuantity(item.getQuantity()-entry.getValue());
			sum+=item.getCost()*entry.getValue();
			itemRepo.save(item);
			Restaurant restaurant= entry.getKey().getRestaurant();
			restaurant.getCustomers().add(customer);
			restaurantRepo.save(restaurant);
		}
		
		
		OrderDetails orderDetails= new OrderDetails();
		orderDetails.setItemsDTO(dtoMap);
		
		
		orderDetails.setOrderDate(LocalDateTime.now());
		orderDetails.setPaymentStatus(Status.valueOf(paymentType));
		orderDetails.setTotalAmount(sum);
		foodCart.setItemsDTO(new HashMap<ItemRestaurantDTO, Integer>());
		customer.getOrders().add(orderDetails);
		orderDetails.setCustomer(customer);
		customerRepo.save(customer);
		
		return orderDetailsRepo.save(orderDetails);
	}

	
	@Override
	public String cancelOrder(String key, Integer orderId) throws OrderDetailsException, LoginException, CustomerException {
		CurrentUserSession currentUserSession = sessionRepo.findByUuid(key);
		
		if(currentUserSession==null) throw new LoginException("Please login to place your order");
		
		Customer customer= customerRepo.findById(currentUserSession.getId()).orElseThrow(()-> new CustomerException("Please login as Customer"));
		
		OrderDetails orderDetails= orderDetailsRepo.findById(orderId).orElseThrow(()-> new OrderDetailsException("Please pass valid order Id"));
		
		if(orderDetails.getCustomer().getCustomerID()!=customer.getCustomerID()) throw new CustomerException("Please pass valid order Id");
		
		LocalDateTime deliverTime = orderDetails.getOrderDate().plusMinutes(20);
		
		if(LocalDateTime.now().isAfter(deliverTime.minusMinutes(10))) {
			throw new OrderDetailsException("Order can not be cancelled, time limit exceeded");
		}
		
		
		
		Map<ItemRestaurantDTO, Integer> dtoMap= orderDetails.getItemsDTO();
		
		
		for(Map.Entry<ItemRestaurantDTO, Integer> entry:dtoMap.entrySet()) {
			Item item=entry.getKey().getItem();
			item.setQuantity(item.getQuantity()+entry.getValue());
			Restaurant restaurant=entry.getKey().getRestaurant();
			restaurant.getItems().add(item);
			
			item.getRestaurants().add(restaurant);
			
			
			
			itemRepo.save(item);
			
			restaurantRepo.save(restaurant);
		}
		
		orderDetailsRepo.delete(orderDetails);
		return "Order cancelled successfully";
	}

	@Override
	public OrderDetails viewOrderByIdByCustomer(String key, Integer orderId) throws OrderDetailsException, CustomerException, LoginException {
		CurrentUserSession currentUserSession = sessionRepo.findByUuid(key);
		
		if(currentUserSession==null) throw new LoginException("Please login to place your order");
		
		Customer customer= customerRepo.findById(currentUserSession.getId()).orElseThrow(()-> new CustomerException("Please login as Customer"));
		
		OrderDetails orderDetails= orderDetailsRepo.findById(orderId).orElseThrow(()-> new OrderDetailsException("Please pass valid order Id"));
		
		if(orderDetails.getCustomer().getCustomerID()!=customer.getCustomerID()) throw new CustomerException("Please pass valid order Id");
		
		return orderDetails;
	}
	@Override
	public OrderDetails viewOrderByIdByRestaurant(String key, Integer orderId) throws OrderDetailsException, RestaurantException, LoginException{
		CurrentUserSession currentUserSession = sessionRepo.findByUuid(key);
		
		if(currentUserSession==null) throw new LoginException("Please login to place your order");
		
		Restaurant restaurant= restaurantRepo.findById(currentUserSession.getId()).orElseThrow(()-> new RestaurantException("Please login as Restaurant"));
		
		Set<Customer> customers= restaurant.getCustomers();
		

		
		for(Customer c: customers) {
			List<OrderDetails> temp = c.getOrders();
			for(OrderDetails o: temp) {
				if(o.getOrderId()==orderId) {
					Double sum=0.0;
					
					Map<ItemRestaurantDTO, Integer> map= o.getItemsDTO();
					
					
					Map<ItemRestaurantDTO, Integer> restaurantOrderDetailsMap= new HashMap<>();
					
					for(Map.Entry<ItemRestaurantDTO, Integer> m: map.entrySet()) {
						if(m.getKey().getRestaurant().getRestaurantId()==restaurant.getRestaurantId()) {
							restaurantOrderDetailsMap.put(m.getKey(), m.getValue());
							sum+=m.getKey().getItem().getCost()*m.getValue();
						}
					}
					if(restaurantOrderDetailsMap.isEmpty()) throw new OrderDetailsException("Please enter valid order id");
					o.setTotalAmount(sum);
					o.setItemsDTO(restaurantOrderDetailsMap);
					return o;
				}
				
			}
		}
		
		throw new OrderDetailsException("Please enter valid order id");
		
		
	}
	@Override
	public List<OrderDetails> viewAllOrdersByRestaurant(String key) throws OrderDetailsException, LoginException , RestaurantException{
		
		CurrentUserSession currentUserSession = sessionRepo.findByUuid(key);
		
		if(currentUserSession==null) throw new LoginException("Please login to place your order");
		
		Restaurant restaurant= restaurantRepo.findById(currentUserSession.getId()).orElseThrow(()-> new RestaurantException("Please login as Restaurant"));
		
		Set<Customer> customers= restaurant.getCustomers();
		
		List<OrderDetails> orders=new ArrayList<>();
		
		for(Customer c: customers) {
			List<OrderDetails> temp = c.getOrders();
			for(OrderDetails o: temp) {
				
				Double sum=0.0;
				
				Map<ItemRestaurantDTO, Integer> map= o.getItemsDTO();
				
				
				Map<ItemRestaurantDTO, Integer> restaurantOrderDetailsMap= new HashMap<>();
				
				for(Map.Entry<ItemRestaurantDTO, Integer> m: map.entrySet()) {
					if(m.getKey().getRestaurant().getRestaurantId()==restaurant.getRestaurantId()) {
						restaurantOrderDetailsMap.put(m.getKey(), m.getValue());
						sum+=m.getKey().getItem().getCost()*m.getValue();
					}
				}
				o.setTotalAmount(sum);
				o.setItemsDTO(restaurantOrderDetailsMap);
				orders.add(o);
				
			}
		}
		if(orders.isEmpty()) throw new OrderDetailsException("Orders Not Found");
		return orders;
	}

	@Override
	public List<OrderDetails> viewAllOrdersByCustomer(String key) throws OrderDetailsException, CustomerException, LoginException {
		CurrentUserSession currentUserSession = sessionRepo.findByUuid(key);
		
		if(currentUserSession==null) throw new LoginException("Please login to place your order");
		
		Customer customer= customerRepo.findById(currentUserSession.getId()).orElseThrow(()-> new CustomerException("Please login as Customer"));
		
		List<OrderDetails> orders= customer.getOrders();
		
		if(orders.isEmpty()) throw new OrderDetailsException("Customer not place any orders");
		return orders;
	}
	
	
	@Override
	public List<OrderDetails> viewAllOrdersByRestaurantByCustomerId(String key, Integer customerId) throws OrderDetailsException, LoginException , RestaurantException, CustomerException{
		CurrentUserSession currentUserSession = sessionRepo.findByUuid(key);
		
		if(currentUserSession==null) throw new LoginException("Please login to place your order");
		
		Restaurant restaurant= restaurantRepo.findById(currentUserSession.getId()).orElseThrow(()-> new RestaurantException("Please login as Restaurant"));
		
		Set<Customer> customers= restaurant.getCustomers();
		Customer customer= null;
		for(Customer c: customers) {
			if(c.getCustomerID()==customerId) {
				customer=c;
			}
		}
		if(customer==null) throw new CustomerException("No orders found for this customers");
		
		List<OrderDetails> orders=new ArrayList<>();
		
		List<OrderDetails> temp = customer.getOrders();
		
		for(OrderDetails o: temp) {
			
			Double sum=0.0;
			
			Map<ItemRestaurantDTO, Integer> map= o.getItemsDTO();
			
			
			Map<ItemRestaurantDTO, Integer> restaurantOrderDetailsMap= new HashMap<>();
			
			for(Map.Entry<ItemRestaurantDTO, Integer> m: map.entrySet()) {
				if(m.getKey().getRestaurant().getRestaurantId()==restaurant.getRestaurantId()) {
					restaurantOrderDetailsMap.put(m.getKey(), m.getValue());
					sum+=m.getKey().getItem().getCost()*m.getValue();
				}
			}
			o.setTotalAmount(sum);
			o.setItemsDTO(restaurantOrderDetailsMap);
			orders.add(o);
			
		}
		if(orders.isEmpty()) throw new OrderDetailsException("Orders Not Found");
		return orders;
	}
}