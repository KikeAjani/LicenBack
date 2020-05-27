package tfg.licensoft.api;

import tfg.licensoft.licenses.License;
import tfg.licensoft.licenses.LicenseService;
import tfg.licensoft.licenses.LicenseSubscription;
import tfg.licensoft.licenses.LicenseSubscriptionService;
import tfg.licensoft.products.Product;
import tfg.licensoft.products.ProductService;
import tfg.licensoft.stripe.StripeServices;
import tfg.licensoft.users.User;
import tfg.licensoft.users.UserService;

import org.springframework.web.bind.annotation.*;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentMethodCollection;
import com.stripe.model.Subscription;
import com.stripe.param.SubscriptionCreateParams;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@CrossOrigin
@RestController
@RequestMapping(value = "/api/users")
public class UserController implements IUserController{

	@Autowired
	private LicenseService licenseServ;
	
	@Autowired
	private LicenseSubscriptionService licenseSubsServ;
	
	@Autowired
	private UserService userServ;
	
	@Autowired
	private ProductService productServ;
	
	@Autowired
	private StripeServices stripeServ;
	
	@Autowired
	private GeneralController generalController;
	
	private static final String CUSTOMER= "customer";
	private static final String DEFAULT_PAYMENT_METHOD= "default_payment_method";
	private static final String REQUIRES_ACTION= "requires_action";

    private static final Logger LOGGER = Logger.getLogger("tfg.licensoft.api.UserController");

    Pattern pattern = Pattern.compile("[A-Za-z0-9_]+");

    
	class SimpleResponse{
		private String response;
		
		

		public SimpleResponse(String response) {
			super();
			this.response = response;
		}

		public String getResponse() {
			return response;
		}

		public void setResponse(String response) {
			this.response = response;
		}
		
	}
	
	public User getRequestUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return this.userServ.findByName(auth.getName());
	}
	
	@PostMapping("{userName}/addCard/{tokenId}")
	public ResponseEntity<SimpleResponse> addCardStripeElements(@PathVariable String userName,@PathVariable String tokenId){
		User user = this.userServ.findByName(userName);
		
		// We don't know which user wants to affect -> Unauthorized
		if(user==null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		// User to be affected != User that made the request
		if(!user.equals(this.getRequestUser())) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		try {
			//Create PaymentMethod
			Map<String, Object> card = new HashMap<>();
		
			card.put("token", tokenId);

			Map<String, Object> params = new HashMap<>();
			params.put("type", "card");
			params.put("card", card);
	
			PaymentMethod paymentMethod = this.stripeServ.createPaymentMethod(params);
			
			params.clear();
			
			List<Object> paymentMethodTypes =  new ArrayList<>();
			paymentMethodTypes.add("card");
			params.put("payment_method_types", paymentMethodTypes);
			params.put(CUSTOMER, user.getCustomerStripeId());
			params.put("payment_method", paymentMethod.getId());
			params.put("confirm", true);

			this.stripeServ.createSetupIntent(params);
			
			params.clear();
			params.put(CUSTOMER, user.getCustomerStripeId());
			this.stripeServ.attachPaymentMethod(paymentMethod, params);
			
			this.setDefaultPaymentMethod(userName, paymentMethod.getId());
			
			SimpleResponse response = new SimpleResponse(paymentMethod.getId());
			return new ResponseEntity<>(response,HttpStatus.OK);

		} catch (StripeException e) {
    		LOGGER.severe(e.getMessage());
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@PutMapping("{userName}/default-card/{paymentMethodId}")
	public ResponseEntity<Boolean> setDefaultPaymentMethod(@PathVariable String userName, @PathVariable String paymentMethodId) {
		try {
			User user = this.userServ.findByName(userName);
			// We don't know which user wants to affect -> Unauthorized
			if(user==null) {
				return new ResponseEntity<>(false,HttpStatus.UNAUTHORIZED);
			}
			// User to be affected != User that made the request
			if(!user.equals(this.getRequestUser())) {
				return new ResponseEntity<>(false,HttpStatus.FORBIDDEN);
			}
			
			Customer c =this.stripeServ.retrieveCustomer(user.getCustomerStripeId());
			
			Map<String, Object> params = new HashMap<>();
			params.put(DEFAULT_PAYMENT_METHOD, paymentMethodId);
			
			Map<String, Object> params2 = new HashMap<>();
			params2.put("invoice_settings", params);
			this.stripeServ.updateCustomer(c, params2);

			return new ResponseEntity<>(true,HttpStatus.OK);

		}catch(StripeException e ) {
    		LOGGER.severe(e.getMessage());
			return new ResponseEntity<>(false,HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@GetMapping("{userName}/default-card")
	public ResponseEntity<SimpleResponse> getDefaultPaymentMethod(@PathVariable String userName) {
		User user = this.userServ.findByName(userName);
		
		// We don't know which user wants to affect -> Unauthorized
		if(user==null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		// User to be affected != User that made the request
		if(!user.equals(this.getRequestUser())) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		Customer c;
		try {
			c = this.stripeServ.retrieveCustomer(user.getCustomerStripeId());
			SimpleResponse r = new SimpleResponse(c.getInvoiceSettings().getDefaultPaymentMethod());
			
			return new ResponseEntity<>(r,HttpStatus.OK);

		} catch (StripeException e) {
    		LOGGER.severe(e.getMessage());
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	
	//Will be subscribed to a M subscription with x free trial days
	@PutMapping("/{userName}/products/{productName}/{type}/addTrial/cards/{token}")
	public ResponseEntity<License> addTrial(@PathVariable String productName, @PathVariable String userName,  @PathVariable String token, @PathVariable String type){
		String safeType = type;
		
		User user = this.userServ.findByName(userName);
		Product product = this.productServ.findOne(productName);
		
		
		// We don't know which user wants to affect -> Unauthorized
		if(user==null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		// User to be affected != User that made the request
		if(!user.equals(this.getRequestUser())) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		
		
		if(product!=null && product.getPlans().get(safeType)!=null) {
			
			try {
				String pM = this.addCardStripeElements(userName,token).getBody().getResponse();

				SubscriptionCreateParams params =
						  SubscriptionCreateParams.builder()
						    .setCustomer(user.getCustomerStripeId())
						    .addItem(
						      SubscriptionCreateParams.Item.builder()
						        .setPlan(product.getPlans().get(safeType)) //Free trial is always monthly subscription
						        .build())
						    .setTrialPeriodDays((long)product.getTrialDays())
						    .setDefaultPaymentMethod(pM)
						    .build();
				Subscription subscription = this.stripeServ.createSubscription(params);
				
				LicenseSubscription license = new LicenseSubscription(true, safeType, product, user.getName(),product.getTrialDays());
				license.setCancelAtEnd(false);  //Trial Periods have automatic renewal
				license.setSubscriptionItemId(subscription.getItems().getData().get(0).getId());
				license.setSubscriptionId(subscription.getId());
				licenseServ.save(license);
				this.setTimerAndEndDate(license.getSerial(),license.getProduct(),product.getTrialDays(),0);
				return new ResponseEntity<>(license,HttpStatus.OK);

			}catch(StripeException e) {
	    		LOGGER.severe(e.getMessage());
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);

		}
	}


	
	@PutMapping("{userName}/products/{productName}/{typeSubs}/addSubscription/renewal/{automaticRenewal}/paymentMethods/{pmId}")
	public ResponseEntity<License> addSubscription(@PathVariable String productName, @PathVariable String typeSubs, @PathVariable String userName, @PathVariable boolean automaticRenewal, @PathVariable String pmId){
    	String cleanedUserName="";
		if(pattern.matcher(userName).matches()) {
    		cleanedUserName=userName;
    	}
		
		Product product = this.productServ.findOne(productName);
		User user = this.userServ.findByName(cleanedUserName);
		
		// We don't know which user wants to affect -> Unauthorized
		if(user==null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		// User to be affected != User that made the request
		if(!user.equals(this.getRequestUser())) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		
		//Check for MB subs if user has a payment method attached
		List<PaymentMethod> lPayments = this.getCardsFromUser(cleanedUserName).getBody();
		if(typeSubs.equals("MB") && lPayments!=null && lPayments.isEmpty()) {
			return new ResponseEntity<>(HttpStatus.PRECONDITION_REQUIRED); //The precondition is to have an attached payment source
		}
		
		String planId=null;
		if(product!=null) {
			Map<String,String> plans = product.getPlans();		 
			planId = plans.get(typeSubs);
		}
		if (planId!=null || product!=null) {
				Map<String, Object> item = new HashMap<>();
				item.put("plan", planId);
				Map<String, Object> items = new HashMap<>();
				items.put("0", item);
				Map<String, Object> expand = new HashMap<>();
				expand.put("0", "latest_invoice.payment_intent");
				Map<String, Object> params = new HashMap<>();
				params.put(CUSTOMER, user.getCustomerStripeId());
				params.put("items", items);
				if(!pmId.equals("default")) {
					params.put(DEFAULT_PAYMENT_METHOD, pmId);
				}
				params.put("expand", expand);
				params.put("cancel_at_period_end", !automaticRenewal);
				Subscription subscription;
				try {
					subscription = this.stripeServ.createSubscription(params);
				} catch (StripeException e) { 
		    		LOGGER.severe(e.getMessage());
					if(e.getCode().equals("resource_missing") && e.getMessage().contains("This customer has no attached payment source")) {
						return new ResponseEntity<>(HttpStatus.PRECONDITION_REQUIRED); //The precondition is to have an attached payment source
					}else {
						return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
					}
				}
				this.productServ.save(product);	
				String status = subscription.getStatus();
				PaymentIntent piReturned = null;

				if (status.equals("active")) {  //Payment finalized
					LicenseSubscription license = new LicenseSubscription(true, typeSubs, product, user.getName(),0);
					license.setCancelAtEnd(!automaticRenewal);
					license.setSubscriptionItemId(subscription.getItems().getData().get(0).getId());
					license.setSubscriptionId(subscription.getId());
					licenseServ.save(license);
					this.setTimerAndEndDate(license.getSerial(),license.getProduct(),0,0);
					
					return new ResponseEntity<>(license,HttpStatus.OK);
				}else if (status.equals("incomplete")) {
					try {
						Invoice s = this.stripeServ.getLatestInvoice(subscription.getLatestInvoice());
						piReturned = this.stripeServ.retrievePaymentIntent(s.getPaymentIntent());
					} catch (StripeException e1) {
						e1.printStackTrace();
						return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
					}
					if(piReturned != null && piReturned.getStatus().equals(REQUIRES_ACTION)) {   //SCA 3DSecure authentication needed
						
						PaymentIntent piReturned2 =null;
				        Map<String, Object> params2 = new HashMap<>();				 
				        params2.put("return_url", this.generalController.appDomain+"/products/"+productName);
				        try {
							piReturned2 = this.stripeServ.confirmPaymentIntent(piReturned, params2);
						} catch (StripeException e) {
				    		LOGGER.severe(e.getMessage());
						}
				        
				        //Added for Sonar analysis
				        if(piReturned2==null) {
							return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
				        }
				        
				        
						LicenseSubscription fakeLicense = new LicenseSubscription(true,typeSubs,product,cleanedUserName,0);
						fakeLicense.setType("RequiresAction");
						fakeLicense.setSerial(piReturned2.getNextAction().getRedirectToUrl().getUrl());
						fakeLicense.setOwner(piReturned2.getId());
						fakeLicense.setSubscriptionId(subscription.getId());
						return new ResponseEntity<>(fakeLicense,HttpStatus.OK);
					}else { //Payment failed
						try {
							this.stripeServ.cancelSubscription(subscription);
						} catch (StripeException e) {
				    		LOGGER.severe(e.getMessage());
						}
						return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
					}
					
				}else { // Other unknown cases
					return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR); 
				}
			
		}else {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
	}
	
	private void setTimerAndEndDate(String licenseSerial, Product product,long trialDays, int nRetry) {	
		LicenseSubscription license = licenseSubsServ.findBySerialAndProduct(licenseSerial, product);
		LOGGER.log(Level.INFO, "Setting {0} timer",license.getSerial());

		
		Timer time = new Timer();
			time.schedule(new TimerTask() {
				
				@Override
				public void run() {
					LicenseSubscription license = licenseSubsServ.findBySerialAndProduct(licenseSerial, product);
					if(license==null) {
						LOGGER.log(Level.INFO, "License is null while setting timer renewal");

					}else {
						if(license.getCancelAtEnd()) {
							LOGGER.log(Level.INFO,"Setting license inactive...");
							license.setActive(false);
							licenseSubsServ.save(license);
						}else {
							try {
								Subscription subs = stripeServ.retrieveSubscription(license.getSubscriptionId());
								String status = subs.getStatus();
								if(status.equals("active")) {  //Already paid
									LOGGER.log(Level.INFO,"Renewal doing on {0}" , license);
									license.renew(trialDays);
									licenseSubsServ.save(license);
									setTimerAndEndDate(licenseSerial,product,0,0);
								}else if (status.equals("past_due")) {  //Needs 3Dsecure 
									Invoice inv = stripeServ.getLatestInvoice(subs.getLatestInvoice());
									if (inv.getStatus().equals(REQUIRES_ACTION) && nRetry<3) {  //Max retries when requires action is 3.
										Date dt = new Date();
										Calendar c = Calendar.getInstance(); 
										c.setTime(dt); 
										c.add(Calendar.DATE, 1);
										dt = c.getTime();
										license.setEndDate(dt);
										setTimerAndEndDate(licenseSerial,product,0,nRetry+1);
										LOGGER.log(Level.INFO,"Subscription {0} needs SCA autenthication. This is the {1} retry of 3 max.", new Object[] { subs.getId(), nRetry }  );
									}
								}else {//Subs canceled because max retries
									stripeServ.cancelSubscription(subs);
								}
							}catch(StripeException ex) {
								ex.printStackTrace();
							}
						}
					}
					
				}
				
			}, 
					license.getEndDate()
					
			);
	}
	
	
	@GetMapping("/{user}/cards")
	public ResponseEntity<List<PaymentMethod>> getCardsFromUser(@PathVariable String user) {
		User u = this.userServ.findByName(user);
		
		
		// We don't know which user wants to affect -> Unauthorized
		if(u==null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		// User to be affected != User that made the request
		if(!u.equals(this.getRequestUser())) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		
		try {
			Map<String, Object> params = new HashMap<>();
			params.put(CUSTOMER, u.getCustomerStripeId());
			params.put("type", "card");
	
			PaymentMethodCollection paymentMethods = this.stripeServ.getPaymentMethodCollection(params);
			List<PaymentMethod> list = new ArrayList<>();
			for(PaymentMethod pm : paymentMethods.getData()) {
					list.add(pm);
			}
			return new ResponseEntity<>(list,HttpStatus.OK);
		}catch (StripeException e) {
    		LOGGER.severe(e.getMessage());
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	@DeleteMapping("/{user}/card/{paymentMethodId}")
	public ResponseEntity<SimpleResponse> deleteStripeCard(@PathVariable String user, @PathVariable String paymentMethodId) {
		User u = this.userServ.findByName(user);
		
		// We don't know which user wants to affect -> Unauthorized
		if(u==null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		// User to be affected != User that made the request
		if(!u.equals(this.getRequestUser())) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		
		try {
			PaymentMethod paymentMethod =  this.stripeServ.retrievePaymentMethod(paymentMethodId);
			this.stripeServ.detachPaymentMethod(paymentMethod);
			SimpleResponse res = new SimpleResponse("true");
			return new ResponseEntity<>(res,HttpStatus.OK);
		}catch(StripeException e) {
    		LOGGER.severe(e.getMessage());
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
		
	}
	
	
	
	//LIFETIME BUY METHODS
	
    @PostMapping("{userName}/paymentIntent/{tokenId}")
    public ResponseEntity<String> payment(@PathVariable String userName,@RequestBody Product product, @PathVariable String tokenId) throws StripeException {
    	
		User u = this.userServ.findByName(userName);
		
		// We don't know which user wants to affect -> Unauthorized
		if(u==null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		// User to be affected != User that made the request
		if(!u.equals(this.getRequestUser())) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		
		Map<String, Object> card = new HashMap<>();
		card.put("token", tokenId);
		Map<String, Object> paramsPM = new HashMap<>();
		paramsPM.put("type", "card");
		paramsPM.put("card", card);

		PaymentMethod paymentMethod =  this.stripeServ.createPaymentMethod(paramsPM);

		
    	Map<String, Object> params = new HashMap<>();
        params.put("amount", product.getPlansPrices().get("L").intValue()*100);
        params.put("payment_method", paymentMethod.getId());
        params.put("currency", "eur");
        params.put("description", "Product bought: " + product.getName());
        

		
        List<Object> paymentMethodTypes = new ArrayList<>();
        paymentMethodTypes.add("card");
        params.put("payment_method_types", paymentMethodTypes);
        params.put(CUSTOMER,u.getCustomerStripeId());
        params.put("receipt_email",u.getEmail());
        PaymentIntent paymentIntent = this.stripeServ.createPaymentIntent(params);
        String paymentStr = paymentIntent.toJson();
        return new ResponseEntity<>(paymentStr, HttpStatus.OK);
    }

	
    
    @PostMapping("{userName}/confirm/{id}/products/{productName}")
    public ResponseEntity<License> confirm(@PathVariable String id, @PathVariable String userName, @PathVariable String productName) throws StripeException {
    	String cleanedUserName="";
    	if(pattern.matcher(userName).matches()) {
    		cleanedUserName=userName;
    	}
    	Product p = this.productServ.findOne(productName);
		User user = this.userServ.findByName(cleanedUserName);

    	ResponseEntity<License> check = this.checkProductAndUser(p, user);
    	if(check.getStatusCode()!=HttpStatus.OK) {
    		return check;
    	}
    	PaymentIntent paymentIntent = this.stripeServ.retrievePaymentIntent(id);
    	if(paymentIntent==null) {
    		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    	}
        Map<String, Object> params = new HashMap<>();
        params.put("return_url", this.generalController.appDomain+"/products/"+productName);
        PaymentIntent piReturned = this.stripeServ.confirmPaymentIntent(paymentIntent, params);
        String status = piReturned.getStatus();
		License license = new License(true, p, cleanedUserName);

        if(status.equals("succeeded")) {
			licenseServ.save(license);
			return new ResponseEntity<>(license,HttpStatus.OK);
        }else if(status.equals("requires_payment_method")) {
            LOGGER.log(Level.INFO,"{0}",piReturned.getLastPaymentError().getMessage());
    		return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);

        }else if(status.equals(REQUIRES_ACTION)) {
        	//Fake license
        	License license2 = new License();
        	license2.setType("RequiresAction");
        	license2.setSerial(piReturned.getNextAction().getRedirectToUrl().getUrl());
    		return new ResponseEntity<>(license2,HttpStatus.OK);
        }else {
        	return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    
    @PostMapping("/{username}/paymentIntents/{paymentIntentId}/products/{product}")
    public ResponseEntity<License> confirm3dsPaymentResponse(@PathVariable String paymentIntentId, @PathVariable String username, @PathVariable String product,  @RequestParam Optional<String> type, 
    		@RequestParam Optional<String> subscriptionId,  @RequestParam Optional<String> automaticRenewal){
    	String cleanedUserName="";
    	if(pattern.matcher(username).matches()) {
    		cleanedUserName=username;
    	}
    	
    	Product p = this.productServ.findOne(product);
		User user = this.userServ.findByName(cleanedUserName);

    	ResponseEntity<License> check = this.checkProductAndUser(p, user);
    	if(check.getStatusCode()!=HttpStatus.OK) {
    		return check;
    	}
    	try {
	    	PaymentIntent pi;
			pi = this.stripeServ.retrievePaymentIntent(paymentIntentId);
	
	    	if(pi==null) {
	    		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
	    	}
	    	
	    	if(pi.getStatus().equals("succeeded")) {
		    	if(!type.isPresent()) {
		    		License license = new License(true, p, cleanedUserName);
		    		this.licenseServ.save(license);
					return new ResponseEntity<>(license,HttpStatus.OK);
	    		}else {
					LicenseSubscription license = new LicenseSubscription(true, type.get(), p, user.getName(),0);
					
					if(subscriptionId.isPresent()) {
						Subscription subscription = this.stripeServ.retrieveSubscription(subscriptionId.get());
						license.setSubscriptionItemId(subscription.getItems().getData().get(0).getId());
						license.setSubscriptionId(subscriptionId.get());
					}
					
	    			if (automaticRenewal.isPresent()) {
		    			boolean automaticR= Boolean.parseBoolean(automaticRenewal.get());
						license.setCancelAtEnd(!automaticR);
	    			}
	    			
					licenseServ.save(license);
					return new ResponseEntity<>(license,HttpStatus.OK);

	    		}
				
	    	}else {
	    		this.stripeServ.cancelPaymentIntent(pi);
				return new ResponseEntity<>(HttpStatus.PRECONDITION_FAILED);
	    	}
    	}catch(StripeException e) {
    		LOGGER.severe(e.getMessage());
    		return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    	}
    	
    	
    	
    }
    
    
    private ResponseEntity<License> checkProductAndUser(Product p , User user){

		// We don't know which user wants to affect -> Unauthorized
		if(user==null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		// User to be affected != User that made the request
		if(!user.equals(this.getRequestUser())) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
    	
    	if(p==null) {
    		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    	}
    	
    	return new ResponseEntity<>(HttpStatus.OK);

    }
    
    private ResponseEntity<License> checkUser(User user){

		// We don't know which user wants to affect -> Unauthorized
		if(user==null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}
		// User to be affected != User that made the request
		if(!user.equals(this.getRequestUser())) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}
		
		return new ResponseEntity<>(HttpStatus.OK);
    }
    
    
    
    @GetMapping("/{username}/subscriptions/{subsId}/paymentMethod")
	public ResponseEntity<SimpleResponse> getPaymentMethodOfSubscription(@PathVariable String username, @PathVariable String subsId) {
		User user = this.userServ.findByName(username);
		HttpStatus status = this.checkUser(user).getStatusCode();
		if (status!=HttpStatus.OK) {
			return new ResponseEntity<>(status);
		}
		try {
			Subscription subs = this.stripeServ.retrieveSubscription(subsId);
			if(subs==null) {
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
			return new ResponseEntity<>(new SimpleResponse(subs.getDefaultPaymentMethod()),HttpStatus.OK);
		} catch (StripeException e) {
    		LOGGER.severe(e.getMessage());
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
		
    }
    
    @PostMapping("/{username}/subscriptions/{subsId}/paymentMethods/{pmId}")
	public ResponseEntity<SimpleResponse> postPaymentMethodOfSubscription(@PathVariable String username, @PathVariable String subsId, @PathVariable String pmId) {
		User user = this.userServ.findByName(username);
		HttpStatus status = this.checkUser(user).getStatusCode();
		if (status!=HttpStatus.OK) {
			return new ResponseEntity<>(status);
		}
		try {
			Subscription subs = this.stripeServ.retrieveSubscription(subsId);
			if(subs==null) {
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
			Map<String, Object> params = new HashMap<>();
			params.put(DEFAULT_PAYMENT_METHOD, pmId);
			Subscription subsReturned= this.stripeServ.updateSubscription(subs, params);
			return new ResponseEntity<>(new SimpleResponse(subsReturned.getDefaultPaymentMethod()),HttpStatus.OK);
		} catch (StripeException e) {
    		LOGGER.severe(e.getMessage());
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
		
    }
    
}
